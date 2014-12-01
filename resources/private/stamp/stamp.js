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
      municipalityAppId: ko.observable(""),
      section: ko.observable("")
    },

    cancelStamping: function() {
      model.stampingMode(false);
      var id = model.appModel.id();
      model.appModel = null;
      model.attachments = null;
      model.authorization = null;

      window.location.hash="!/application/" + id + "/attachments";
      repository.load(id);
    }
  };

  function setStampFields() {
    if ( !model.stampFields.organization ) {
      model.stampFields.organization = ko.observable(model.appModel.organizationName());
    }

    if ( _.isEmpty(model.stampFields.municipalityAppId()) && model.appModel.verdicts && !_.isEmpty(model.appModel.verdicts()) ) {
        model.stampFields.municipalityAppId(_.first(model.appModel.verdicts())["kuntalupatunnus"]());
    }

    if ( _.isEmpty(model.stampFields.section()) && model.appModel.verdicts && !_.isEmpty(model.appModel.verdicts()) ) {
      var verdict = ko.mapping.toJS(model.appModel.verdicts()[0]);
      if ( verdict.paatokset[0] && verdict.paatokset[0].poytakirjat[0] && verdict.paatokset[0].poytakirjat[0].pykala ) {
        model.stampFields.section(verdict.paatokset[0].poytakirjat[0].pykala);
      }
    }
  };

  function initStamp(appModel) {
    model.appModel = appModel;
    model.attachments = model.appModel.attachments();
    model.authorization = authorization.create();
    model.authorization.refresh(model.appModel.id());

    setStampFields();

    window.location.hash="!/stamping/" + model.appModel.id();
  }

  hub.onPageChange("stamping", function() {
    if ( pageutil.subPage() ) {
      if ( !model.appModel || model.appModel.id() !== pageutil.subPage() ) {
        // refresh
        model.stampingMode(false);

        var appId = pageutil.subPage();
        repository.load(appId, null, function(application) {
          model.authorization = authorization.create();
          model.appModel = new LUPAPISTE.ApplicationModel(model.authorization);
          model.authorization.refresh(application);

          ko.mapping.fromJS(application, {}, model.appModel);

          model.attachments = model.appModel.attachments();

          setStampFields();

          model.stampingMode(true);
        });
      } else { // appModel already initialized, show stamping
        model.stampingMode(true);
      }
    } else {
      error("No application ID provided for stamping");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
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
