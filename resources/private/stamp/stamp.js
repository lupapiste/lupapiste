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
      transparency: ko.observable(),
      extraInfo: ko.observable(""),
      buildingId: ko.observable(""),
      kuntalupatunnus: ko.observable(""),
      section: ko.observable(""),
      buildingIdList: ko.observableArray()
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
    if ( !model.stampFields.organization ) {
      model.stampFields.organization = ko.observable(model.appModel.organizationName());
    }

    if ( model.appModel.verdicts && !_.isEmpty(model.appModel.verdicts()) ) {
      model.stampFields.kuntalupatunnus(_.first(model.appModel.verdicts()).kuntalupatunnus());
      var verdict = ko.mapping.toJS(model.appModel.verdicts()[0]);
      if ( verdict.paatokset[0] && verdict.paatokset[0].poytakirjat[0] && verdict.paatokset[0].poytakirjat[0].pykala ) {
        var pykala = verdict.paatokset[0].poytakirjat[0].pykala;
        pykala = _.contains(pykala, "\u00a7") ? pykala : "\u00a7 " + pykala;
        model.stampFields.section(pykala);
      } else {
        model.stampFields.section("\u00a7");
      }
    } else {
      model.stampFields.kuntalupatunnus("");
      model.stampFields.section("\u00a7");
    }

    if ( model.appModel.buildings ) {
      model.stampFields.buildingIdList(model.appModel.buildings());
    }
  }

  function initStamp(appModel) {
    model.appModel = appModel;
    model.attachments = model.appModel.attachments();
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

          model.attachments = model.appModel.attachments();

          setStampFields();

          model.stampingMode(true);
        });
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
