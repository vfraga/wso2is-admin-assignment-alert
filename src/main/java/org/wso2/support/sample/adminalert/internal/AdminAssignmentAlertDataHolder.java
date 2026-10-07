package org.wso2.support.sample.adminalert.internal;

import org.wso2.carbon.identity.event.services.IdentityEventService;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;

/**
 * Holds OSGi services used by the admin-assignment alert handler.
 */
public class AdminAssignmentAlertDataHolder {

    private static final AdminAssignmentAlertDataHolder instance = new AdminAssignmentAlertDataHolder();

    private RoleManagementService roleManagementService;
    private IdentityEventService identityEventService;

    private AdminAssignmentAlertDataHolder() {
    }

    public static AdminAssignmentAlertDataHolder getInstance() {

        return instance;
    }

    public RoleManagementService getRoleManagementService() {

        return roleManagementService;
    }

    public void setRoleManagementService(RoleManagementService roleManagementService) {

        this.roleManagementService = roleManagementService;
    }

    public IdentityEventService getIdentityEventService() {

        return identityEventService;
    }

    public void setIdentityEventService(IdentityEventService identityEventService) {

        this.identityEventService = identityEventService;
    }
}
