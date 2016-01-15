var externalApiTools = (function() {
  "use strict";

  /*
   * Returns application as PermitFilter object. Used with external JS APIs.
   * @return {PermitFilter}
   */
  function toPermitFilter(application) {
    var result = _.pick(application, ["id", "location", "address", "municipality", "applicant"]);
    result.type = application.infoRequest ? "inforequest" : "application";
    result.authority = application.authority.id ? application.authority.lastName + " " + application.authority.firstName : "";
    var op = util.getIn(application, ["primaryOperation", "name"]);
    result.operation = op ? loc(["operations", op]) : "";
    return result;
  }

  return { toExternalPermit: toPermitFilter};
})();
