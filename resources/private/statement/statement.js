(function() {
  "use strict";

  var applicationId = ko.observable();
  var application = ko.observable();
  var statementId = ko.observable();
  var authModel = authorization.create();

  var tabs = ko.pureComputed(function() {
    if (authModel.ok("statement-is-replyable")) {
      return ["statement", "reply"];
    } else if (authModel.ok("authorized-for-requesting-statement-reply")) {
      return ["statement", "reply-request"];
    } else {
      return ["statement"];
    }
  });

  var defaultTab = "statement"

  var selectedTab = ko.observable(defaultTab);

  var selectTab = function(tab) {
    selectedTab(tab);
    pageutil.openPage("statement", [applicationId(), statementId(), tab].join("/"));
  };

  repository.loaded(["statement"], function(app) {
    if (applicationId() === app.id) {
      application(app);
      authModel.refresh(app, {statementId: statementId()});
    }
  });

  hub.onPageLoad("statement", function(e) {
    applicationId(e.pagePath[0]);
    statementId(e.pagePath[1]);
    selectedTab(e.pagePath[2] || defaultTab);
    repository.load(applicationId());
  });

  $(function() {
    $("#statement").applyBindings({
      application: application,
      service: new LUPAPISTE.StatementService({statementId: statementId, application: application}),
      authModel: authModel,
      selectedTab: selectedTab,
      selectTab: selectTab,
      tabs: tabs
    });
  });

})();
