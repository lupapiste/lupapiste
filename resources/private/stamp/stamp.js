var stamping = (function() {
  "use strict";

  var model = {
    stampingMode: ko.observable(false),

    appModel: null,
    attachments: null,
    stampFields: {
      text: ko.observable(loc("stamp.verdict")),
      date: ko.observable(new Date()),
      organization: null,
      xMargin: ko.observable("10"),
      yMargin: ko.observable("85"),
      transparency: ko.observable()
    },

    cancelStamping: function() {
      model.stampingMode(false);
      var id = model.appModel.id();
      model.appModel = null;
      model.attachments = null;

      window.location.hash='!/application/' + id + '/attachments';
      repository.load(id);
    }
  };


  function initStamp(appModel) {
    model.appModel = appModel;
    model.attachments = model.appModel.attachments();

    if ( !model.stampFields.organization ) {
      model.stampFields.organization = ko.observable(model.appModel.organizationName());
    }

    window.location.hash='!/stamping/' + model.appModel.id();
  };

  hub.onPageChange('stamping', function(e) {
    if ( !model.appModel ) {
      if ( e.pagePath[0] ) {
        var appId = e.pagePath[0];
        repository.load(appId, null, function(application) {
          model.appModel = new LUPAPISTE.ApplicationModel(authorization.create());
          ko.mapping.fromJS(application, {}, model.appModel);

          model.attachments = model.appModel.attachments();
          model.stampFields.organization = ko.observable(model.appModel.organizationName());

          model.stampingMode(model.appModel !== null); // show
        });
      } else {
        error("No application ID provided for stamping");
        LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
      }
    } else { // appModel already initialized, show stamping
      model.stampingMode(true);
    }
  });

  hub.subscribe('start-stamping', function(param) {
    initStamp(param.application);
  });

  ko.components.register('stamping-component', {
    viewModel: LUPAPISTE.StampModel,
    template: {element: "stamp-attachments-template"}
  });

  $(function() {
    $("#stamping-container").applyBindings(model);
  });
})();