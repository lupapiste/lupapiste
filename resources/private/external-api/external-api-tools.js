var externalApiTools = (function() {
  "use strict";

  var permitSkeleton = {id: "",
                        address: "",
                        applicant: "",
                        authority: "",
                        location: {x: 0, y: 0},
                        municipality: "",
                        operation: "",
                        type: "",
                        permitType: ""};
  /*
   * Returns application as PermitFilter object. Used with external JS APIs.
   * @return {PermitFilter}
   */
  function toPermitFilter(application) {
    var result = _.pick(application, ["id", "location", "address", "municipality", "applicant", "permitType"]);
    result.type = application.infoRequest ? "inforequest" : "application";
    var handler = _.defaults( _.first( application.handlers ),
                              {lastName: "", firstName: ""});
    result.authority = _.trim(sprintf( "%s %s", handler.lastName, handler.firstName ));
    var op = util.getIn(application, ["primaryOperation", "name"]);
    result.operation = op ? loc(["operations", op]) : "";
    return _.defaults(result, permitSkeleton);
  }

  return { toExternalPermit: toPermitFilter};
})();
