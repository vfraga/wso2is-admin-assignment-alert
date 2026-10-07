# IS Admin Assignment Alert Handler (WSO2 Identity Server 7.1.0)

Support sample. A custom WSO2 Identity Server event handler that raises a real-time alert whenever a
user is granted administrator access, that is, whenever a user is added to a watched privileged role
(by default `admin` and `Administrator`). The alert is emitted two ways:

1. A `WARN` log line carrying the marker `[ADMIN-ALERT]`, for SIEM or log-based alerting.
2. An email through the Identity Server notification framework, for direct email alerting.

It is provided as-is to illustrate the event-handler extension pattern. Review and adapt it before
any production use.

## How it works

The handler extends `org.wso2.carbon.identity.event.handler.AbstractEventHandler` and subscribes to:

- `POST_UPDATE_USER_LIST_OF_ROLE_V2_EVENT`: fired when users are added to or removed from a role. The
  handler resolves the role name and, if it is one of the watched roles and users were added, alerts
  for each added user.
- `POST_ADD_ROLE_V2_EVENT`: fired when a role is created. Alerts if a watched role is created.
- `POST_ADD_USER`: fired when a user is created. Off by default; enable with `notify_on_user_add`.

The email is sent by publishing a `TRIGGER_NOTIFICATION` event to the notification framework with a
template type and recipient, so the server's existing SMTP sender and templates are reused.

## Build

Requires JDK 11 and Maven. From the project root:

```
mvn clean package
```

The bundle is produced at `target/org.wso2.support.sample.adminalert-1.0.0.jar`.

## Deploy

1. Copy the bundle to `<IS_HOME>/repository/components/dropins/`.
2. Restart the server.

## Configure

Add an `[[event_handler]]` block to `<IS_HOME>/repository/conf/deployment.toml`:

```toml
[[event_handler]]
name = "adminAssignmentAlertHandler"
subscriptions = ["POST_UPDATE_USER_LIST_OF_ROLE_V2_EVENT", "POST_ADD_ROLE_V2_EVENT", "POST_ADD_USER"]
properties.watched_roles = "admin,Administrator"
properties.alert_recipient = "secops@example.com"
properties.template_type = "adminRoleAssignmentAlert"
properties.notify_on_user_add = "false"
```

| Property | Meaning | Default |
| --- | --- | --- |
| `watched_roles` | Comma-separated role names to alert on | `admin,Administrator` |
| `alert_recipient` | Email address the alert is sent to. If empty, only the log line is emitted | (none) |
| `template_type` | Notification template type used for the email | `adminRoleAssignmentAlert` |
| `notify_on_user_add` | Also alert when any user is created | `false` |

The email alert needs two more things: an SMTP sender and a matching email template.

### SMTP sender

If the server does not already have an email sender, configure one in `deployment.toml`, then
restart:

```toml
[output_adapter.email]
from_address = "is-alerts@example.com"
hostname = "your.smtp.host"
port = 587
enable_start_tls = true
enable_authentication = true
username = "..."
password = "..."
```

### Email template

Create an email template whose type matches the `template_type` value above
(`adminRoleAssignmentAlert`). The template body can use the placeholders `{{reason}}`,
`{{role-name}}`, `{{subject-user-id}}` and `{{initiator}}`.

Option A, Console: go to Email Templates, add a new template type named exactly
`adminRoleAssignmentAlert`, then add a template for your locale (for example English (United
States)) with a subject and body that use the placeholders above.

Option B, Management API: create the type with the template embedded in a single call (a POST to
`.../templates` is not supported, and PUT only updates an existing template, so the template must
be supplied at type-creation time):

```bash
curl -k -H "Authorization: Bearer <admin-token>" -H "Content-Type: application/json" \
  https://<host>:<port>/api/server/v1/email/template-types \
  -d '{
    "displayName": "adminRoleAssignmentAlert",
    "templates": [{
      "contentType": "text/html",
      "subject": "[IS Alert] Administrator access granted",
      "body": "<p>An administrator-level assignment occurred on WSO2 Identity Server.</p><p>Reason: {{reason}}<br/>Role: {{role-name}}<br/>Subject user id: {{subject-user-id}}<br/>Performed by: {{initiator}}</p>",
      "id": "en_US"
    }]
  }'
```

If `alert_recipient` is left empty, the handler still writes the `[ADMIN-ALERT]` log line but
sends no email, so the SMTP sender and template are only needed for the email channel.

Restart the server after changing `deployment.toml`. Template changes made through the Console or
the API take effect without a restart.

## Test

1. Sign in to the Console as the super admin and add any user to the `Administrator` role.
2. Confirm the log line in `<IS_HOME>/repository/logs/wso2carbon.log`:
   `[ADMIN-ALERT] ROLE_ASSIGNED | role=Administrator | subject=<userId> | tenant=carbon.super | initiator=admin`
3. Confirm the alert email arrived at `alert_recipient`.

## Notes and limitations

- The handler alerts on assignment to the Console `Administrator` role and the organization `admin`
  role. To also alert on the primary `admin` group, note that adding a member to that group is
  processed as an update to the internal admin role and is covered by the same event; confirm the
  resolved role name is in `watched_roles`.
- Alert failures (for example a missing email template) never interrupt the originating operation;
  the log line is always written.
- Enable the v2 audit logs (`[system.parameter] enableV2AuditLogs = "true"`) if you also want the
  assignment recorded in the audit log.
