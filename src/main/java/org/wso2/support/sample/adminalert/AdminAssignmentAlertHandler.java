/*
 * Sample custom event handler for WSO2 Identity Server 7.1.0.
 *
 * Raises a real-time alert (log line + email through the IS notification framework) whenever a user
 * is added to a watched privileged role (by default "admin" and "Administrator"), i.e. whenever an
 * account is granted administrator access. Optionally also alerts when a new user is created.
 *
 * This is support sample code, provided as-is, to illustrate the event-handler extension pattern.
 */
package org.wso2.support.sample.adminalert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.role.v2.mgt.core.exception.IdentityRoleManagementException;
import org.wso2.support.sample.adminalert.internal.AdminAssignmentAlertDataHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event handler that alerts on privileged-role assignment.
 */
public class AdminAssignmentAlertHandler extends AbstractEventHandler {

    private static final Log log = LogFactory.getLog(AdminAssignmentAlertHandler.class);

    // Configuration keys, read from the [[event_handler]] properties block in deployment.toml.
    private static final String PROP_WATCHED_ROLES = "watched_roles";
    private static final String PROP_ALERT_RECIPIENT = "alert_recipient";
    private static final String PROP_TEMPLATE_TYPE = "template_type";
    private static final String PROP_NOTIFY_ON_USER_ADD = "notify_on_user_add";

    private static final String DEFAULT_WATCHED_ROLES = "admin,Administrator";
    private static final String DEFAULT_TEMPLATE_TYPE = "adminRoleAssignmentAlert";

    // Notification framework property keys (consumed by the shipped notification handler).
    private static final String SEND_TO = "send-to";
    private static final String TEMPLATE_TYPE = "TEMPLATE_TYPE";

    @Override
    public String getName() {

        return "adminAssignmentAlertHandler";
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        String eventName = event.getEventName();
        Map<String, Object> properties = event.getEventProperties();
        String tenantDomain = (String) properties.get(IdentityEventConstants.EventProperty.TENANT_DOMAIN);

        if (IdentityEventConstants.Event.POST_UPDATE_USER_LIST_OF_ROLE_V2_EVENT.equals(eventName)) {
            handleRoleUserListUpdate(properties, tenantDomain);
        } else if (IdentityEventConstants.Event.POST_ADD_ROLE_V2_EVENT.equals(eventName)) {
            handleRoleAdd(properties, tenantDomain);
        } else if (IdentityEventConstants.Event.POST_ADD_USER.equals(eventName)) {
            if (isNotifyOnUserAdd()) {
                String userName = (String) properties.get(IdentityEventConstants.EventProperty.USER_NAME);
                alert("USER_CREATED", userName, null, tenantDomain);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRoleUserListUpdate(Map<String, Object> properties, String tenantDomain)
            throws IdentityEventException {

        Object newUsersObj = properties.get(IdentityEventConstants.EventProperty.NEW_USER_ID_LIST);
        List<String> newUserIds = (newUsersObj instanceof List) ? (List<String>) newUsersObj : new ArrayList<>();
        if (newUserIds.isEmpty()) {
            return;
        }
        String roleId = (String) properties.get(IdentityEventConstants.EventProperty.ROLE_ID);
        String roleName = resolveRoleName(roleId, tenantDomain);
        if (roleName == null || !getWatchedRoles().contains(roleName)) {
            return;
        }
        for (String userId : newUserIds) {
            alert("ROLE_ASSIGNED", userId, roleName, tenantDomain);
        }
    }

    private void handleRoleAdd(Map<String, Object> properties, String tenantDomain) throws IdentityEventException {

        String roleName = (String) properties.get(IdentityEventConstants.EventProperty.ROLE_NAME);
        if (roleName == null) {
            roleName = resolveRoleName((String) properties.get(IdentityEventConstants.EventProperty.ROLE_ID),
                    tenantDomain);
        }
        if (roleName != null && getWatchedRoles().contains(roleName)) {
            alert("PRIVILEGED_ROLE_CREATED", null, roleName, tenantDomain);
        }
    }

    private String resolveRoleName(String roleId, String tenantDomain) {

        if (roleId == null) {
            return null;
        }
        try {
            return AdminAssignmentAlertDataHolder.getInstance().getRoleManagementService()
                    .getRoleNameByRoleId(roleId, tenantDomain);
        } catch (IdentityRoleManagementException e) {
            log.warn("Could not resolve role name for role id: " + roleId + " in tenant: " + tenantDomain
                    + ". Skipping alert.", e);
            return null;
        }
    }

    /**
     * Emits the alert: a WARN log line (for SIEM / log-based alerting) and an email through the IS
     * notification framework (for direct email alerting).
     */
    private void alert(String reason, String subjectUserId, String roleName, String tenantDomain) {

        String initiator = getInitiator();
        String summary = "[ADMIN-ALERT] " + reason
                + (roleName != null ? " | role=" + roleName : "")
                + (subjectUserId != null ? " | subject=" + subjectUserId : "")
                + " | tenant=" + tenantDomain
                + " | initiator=" + initiator;
        // 1. Log-based signal. A SIEM or log watcher can alert on the [ADMIN-ALERT] marker.
        log.warn(summary);

        // 2. Email through the notification framework (only if a recipient is configured).
        String recipient = getProperty(PROP_ALERT_RECIPIENT, null);
        if (recipient == null || recipient.trim().isEmpty()) {
            return;
        }
        try {
            Map<String, Object> props = new HashMap<>();
            props.put(SEND_TO, recipient);
            props.put(TEMPLATE_TYPE, getProperty(PROP_TEMPLATE_TYPE, DEFAULT_TEMPLATE_TYPE));
            props.put(IdentityEventConstants.EventProperty.TENANT_DOMAIN, tenantDomain);
            // Placeholders available to the email template.
            props.put("reason", reason);
            props.put("role-name", roleName != null ? roleName : "");
            props.put("subject-user-id", subjectUserId != null ? subjectUserId : "");
            props.put("initiator", initiator);
            Event notification = new Event(IdentityEventConstants.Event.TRIGGER_NOTIFICATION, props);
            AdminAssignmentAlertDataHolder.getInstance().getIdentityEventService().handleEvent(notification);
            if (log.isDebugEnabled()) {
                log.debug("Admin assignment alert email triggered to " + recipient);
            }
        } catch (IdentityEventException e) {
            // Never let alerting failure break the originating operation.
            log.error("Failed to trigger admin assignment alert email. The log-based alert was still written.", e);
        }
    }

    private String getInitiator() {

        try {
            String user = org.wso2.carbon.context.CarbonContext.getThreadLocalCarbonContext().getUsername();
            return user != null ? user : "unknown";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private Set<String> getWatchedRoles() {

        String value = getProperty(PROP_WATCHED_ROLES, DEFAULT_WATCHED_ROLES);
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean isNotifyOnUserAdd() {

        return Boolean.parseBoolean(getProperty(PROP_NOTIFY_ON_USER_ADD, "false"));
    }

    private String getProperty(String key, String defaultValue) {

        if (configs != null) {
            Properties moduleProperties = configs.getModuleProperties();
            if (moduleProperties != null) {
                // The identity-event config builder stores module properties with the handler name
                // as a prefix (e.g. "adminAssignmentAlertHandler.alert_recipient"). Try the prefixed
                // key first, then the bare key as a fallback.
                String value = moduleProperties.getProperty(getName() + "." + key);
                if (value == null) {
                    value = moduleProperties.getProperty(key);
                }
                if (value != null) {
                    return value;
                }
            }
        }
        return defaultValue;
    }
}
