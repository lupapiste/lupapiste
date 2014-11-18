var stamping = (function() {
  "use strict";

  var postVerdictStates = {verdictGiven:true, constructionStarted:true, closed:true}; // TODO make global var

  var model = {
    stampingMode: ko.observable(false),

    appModel: null,
    attachments: null,
    stampFields: {
      text: ko.observable(loc("stamp.verdict")),
      date: ko.observable(new Date()),
      organization: null,
      xMargin: ko.observable("10"),
      yMargin: ko.observable("85")
    },

    cancelStamping: function() {
      model.stampingMode(false);
      var id = model.appModel.id();
      model.appModel = null;
      model.attachments = null;

      window.location.hash='#!/application/' + id + '/attachments';
      repository.load(id);
    }
  };


  function initStamp(appModel, attachments, stampFields) {
    model.appModel = appModel;
    model.attachments = attachments;
    if ( !model.stampFields.organization ) {
      model.stampFields.organization = ko.observable(model.appModel.organizationName());
    }

    window.location.hash='#!/stamping/' + model.appModel.id();

  };

  hub.onPageChange('stamping', function(e) {
    if ( !model.appModel ) {
      if ( e.pagePath[0] ) {
        var appId = e.pagePath[0];
        repository.load(appId, null, function(application) {
          model.appModel = new LUPAPISTE.ApplicationModel(authorization.create());
          ko.mapping.fromJS(application, {}, model.appModel);

          var filtered = _.filter(ko.mapping.toJS(model.appModel.attachments), function(attachment) {
            return model.appModel.inPostVerdictState() ? postVerdictStates[attachment.applicationState] : !postVerdictStates[attachment.applicationState];
          });

          model.attachments = ko.observableArray(attachmentUtils.getGroupByOperation(filtered, true, model.appModel.allowedAttachmentTypes()));
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
    initStamp(param.application, param.attachments);
  });

  ko.components.register('stamping-component', {
    viewModel: LUPAPISTE.StampModel,
    template: {element: "dialog-stamp-attachments"}
  });

  $(function() {
    $("#stamping-container").applyBindings(model);
  });
})();