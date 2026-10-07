package org.wso2.support.sample.adminalert.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.support.sample.adminalert.AdminAssignmentAlertHandler;

/**
 * OSGi declarative-services component that registers {@link AdminAssignmentAlertHandler} as an
 * {@link AbstractEventHandler} service. The SCR descriptor is generated from these annotations at
 * build time by the maven-scr-plugin / maven-bundle-plugin.
 */
@Component(
        name = "org.wso2.support.sample.adminalert.component",
        immediate = true)
public class AdminAssignmentAlertComponent {

    private static final Log log = LogFactory.getLog(AdminAssignmentAlertComponent.class);

    @Activate
    protected void activate(final ComponentContext context) {

        try {
            context.getBundleContext().registerService(AbstractEventHandler.class.getName(),
                    new AdminAssignmentAlertHandler(), null);
            log.info("AdminAssignmentAlertHandler registered successfully.");
        } catch (Throwable e) {
            log.error("Error while registering AdminAssignmentAlertHandler.", e);
        }
    }

    @Deactivate
    protected void deactivate(final ComponentContext ignored) {

        log.info("AdminAssignmentAlert bundle deactivated.");
    }

    @Reference(
            name = "role.management.service",
            service = RoleManagementService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRoleManagementService")
    protected void setRoleManagementService(final RoleManagementService roleManagementService) {

        AdminAssignmentAlertDataHolder.getInstance().setRoleManagementService(roleManagementService);
    }

    protected void unsetRoleManagementService(final RoleManagementService roleManagementService) {

        AdminAssignmentAlertDataHolder.getInstance().setRoleManagementService(null);
    }

    @Reference(
            name = "identity.event.service",
            service = IdentityEventService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetIdentityEventService")
    protected void setIdentityEventService(final IdentityEventService identityEventService) {

        AdminAssignmentAlertDataHolder.getInstance().setIdentityEventService(identityEventService);
    }

    protected void unsetIdentityEventService(final IdentityEventService identityEventService) {

        AdminAssignmentAlertDataHolder.getInstance().setIdentityEventService(null);
    }
}
