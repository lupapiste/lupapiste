(function() {
  "use strict";

  var applicationId = ko.observable();
  var application = ko.observable();
  var statementId = ko.observable();
  var authModel = authorization.create();
  var service = new LUPAPISTE.StatementService({statementId: statementId, application: application});

  var defaultAttachmentType = {"type-group": "ennakkoluvat_ja_lausunnot", "type-id": "lausunnon_liite"};
  var availableGroups = [
    "ennakkoluvat_ja_lausunnot",
    "erityissuunnitelmat",
    "rakennuspaikka",
    "pelastusviranomaiselle_esitettavat_suunnitelmat",
    "yleiset-alueet",
    "muut"
  ];

  var tabs = ko.pureComputed(function() {
    // these are matched in Reitit frontend controller
    if (authModel.ok("statement-is-replyable")) {
      return ["statement", "reply"];
    } else if (authModel.ok("authorized-for-requesting-statement-reply")) {
      return ["statement", "reply-request"];
    } else {
      return ["statement"];
    }
  });

  var defaultTab = "statement";

  var selectedTab = ko.observable(defaultTab);

  var selectTab = function(tab) {
    selectedTab(tab);
    pageutil.openPage("statement", [applicationId(), statementId(), tab].join("/"));
  };

  var targeted = {
    defaults: {
      target: ko.observable(),
      type: ko.pureComputed(function() {
        if (util.getIn(service, ["data", "in-attachment"])) {
          return {"type-group": "ennakkoluvat_ja_lausunnot", "type-id": "lausunto"};
        } else {
          return defaultAttachmentType;
        }
      }),
      contents: ko.pureComputed(function() {
        var t = util.getIn(service, ["data", "person", "text"]);
        if (t && !util.getIn(service, ["data", "in-attachment"])) {
          t += ", " + loc("liite");
        }
        return t;
      })
    },
    disabledCols: ["sign", "drawing"],
    canAdd: ko.pureComputed(function() {
      return authModel.ok("statement-attachment-allowed");
    }),
    typeGroups: ko.observableArray(availableGroups),
    dropZoneSectionId: "statement",
    template: "targeted-attachments-statement"
  };

  repository.loaded(["statement"], function(app) {
    if (applicationId() === app.id) {
      application(app);
      authModel.refresh(app, {statementId: statementId()});
      targeted.defaults.target({type: "statement", id: statementId()});
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
      service: service,
      authModel: authModel,
      selectedTab: selectedTab,
      selectTab: selectTab,
      tabs: tabs,
      targeted: targeted
    });
  });

})();
