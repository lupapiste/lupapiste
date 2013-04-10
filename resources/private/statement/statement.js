var statement = (function() {
  "use strict";

  var applicationId = null;
  var statementId = null;

  hub.onPageChange("statement", function(e) {
    applicationId = e.pagePath[0];
    statementId = e.pagePath[1];
    console.log(applicationId,statementId,"here");
    repository.load(applicationId);
  });

  $(function() {
    ko.applyBindings({
      statementModel: true
    }, $("#statement")[0]);
  });

})();
