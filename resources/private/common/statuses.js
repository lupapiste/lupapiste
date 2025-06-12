/**
 * Mapping from application and task states for status icons: missing, new, ok
 * TODO: This could/should be refactored out when the lupicons are fully in
 * in production. Those are not directly added here, because the client code
 * may have some specific dependencies/assumptions.
 */

LUPAPISTE.statuses = {
  requires_user_action: "missing",
  requires_authority_action: "new",
  ok: "ok",
  sent: "ok"
};

LUPAPISTE.statusIcon = function( status ) {
  "use strict";
  var cls = {
    missing: "attention",
    "new": "star",
    ok: "check",
    rejected: "minus"
  }[status];

  return cls ? "lupicon-circle-" + cls : "";
};
