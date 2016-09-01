var stamping = (function() {
  "use strict";

  var model = {
    stampingMode: ko.observable(false),
    authorization: null,
    appModel: null,
    attachments: null,
    stampFields: {
      text: ko.observable(loc("stamp.verdict")),
      date: ko.observable(new Date()),
      organization: null,
      xMargin: ko.observable("10"),
      yMargin: ko.observable("85"),
      page: ko.observable("first"),
      transparency: ko.observable(),
      extraInfo: ko.observable(""),
      includeBuildings: ko.observable(false),
      kuntalupatunnus: ko.observable(""),
      section: ko.observable("")
    },

    cancelStamping: function() {
      var id = pageutil.subPage();
      model.stampingMode(false);
      model.appModel = null;
      model.attachments = null;
      model.authorization = null;
      pageutil.openPage("application/" + id, "attachments");
    },

    resetStamping: function() {
      model.stampingMode(false);
      model.appModel = null;
      model.attachments = null;
      model.authorization = null;

      hub.send("page-load", { pageId: "stamping" });
    }
  };

  function setStampFields() {
    var verdict = util.getIn(model.appModel._js, ["verdicts", 0]);

    if (!model.stampFields.organization) {
      model.stampFields.organization = ko.observable(model.appModel.organizationName());
    }

    if (verdict) {
      model.stampFields.kuntalupatunnus(verdict.kuntalupatunnus);
      var pykala = util.getIn(verdict, ["paatokset", 0, "poytakirjat", 0, "pykala"]);

      if (pykala) {
        model.stampFields.section(_.includes(pykala, "\u00a7") ? pykala : "\u00a7 " + pykala);
      } else {
        model.stampFields.section("\u00a7");
      }
    } else {
      model.stampFields.kuntalupatunnus("");
      model.stampFields.section("\u00a7");
    }
  }

  function initStamp(appModel) {
    model.appModel = appModel;
    model.attachments = lupapisteApp.services.attachmentsService.attachments;
    model.authorization = lupapisteApp.models.applicationAuthModel;

    setStampFields();
    pageutil.openPage("stamping", model.appModel.id());
  }

  hub.onPageLoad("stamping", function() {
    if ( pageutil.subPage() ) {
      if ( !model.appModel || model.appModel.id() !== pageutil.subPage() ) {
        // refresh
        model.stampingMode(false);

        var appId = pageutil.subPage();
        repository.load(appId, _.noop, function(application) {
          lupapisteApp.setTitle(application.title);

          model.authorization = lupapisteApp.models.applicationAuthModel;
          model.appModel = lupapisteApp.models.application;

          ko.mapping.fromJS(application, {}, model.appModel);
          model.appModel._js = application;

          model.attachments = lupapisteApp.services.attachmentsService.attachments;

          setStampFields();

          model.stampingMode(true);
        }, true);
      } else { // appModel already initialized, show stamping
        model.stampingMode(true);
        lupapisteApp.setTitle(model.appModel.title());
      }
    } else {
      error("No application ID provided for stamping");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
  });

  hub.onPageUnload("stamping", function() {
    model.stampingMode(false);
    model.appModel = null;
    model.attachments = null;
    model.authorization = null;
  });

  hub.subscribe("start-stamping", function(param) {
    initStamp(param.application);
  });

  ko.components.register("stamping-component", {
    viewModel: LUPAPISTE.StampModel,
    template: {element: "stamp-attachments-template"}
  });

  $(function() {
    $("#stamping-container").applyBindings(model);
  });
})();
