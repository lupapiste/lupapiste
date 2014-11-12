var stamping = (function() {

  var self = this;

  var postVerdictStates = {verdictGiven:true, constructionStarted:true, closed:true}; // TODO make global var

  self.stampingMode = ko.observable(false);
  self.appModel = null;
  self.attachments = null;

  self.cancelStamping = function() {
    self.stampingMode(false);
    var id = self.appModel.id();
    self.appModel = null;
    self.attachments = null;

    window.location.hash='#!/application/' + id + '/attachments';
    repository.load(id);
  };

  function initStamp(appModel, attachments) {
    self.appModel = appModel;
    self.attachments = attachments;

    window.location.hash='#!/stamping/' + self.appModel.id();

  };

  hub.onPageChange('stamping', function(e) {
    if ( !self.appModel ) {
      if ( e.pagePath[0] ) {
        var appId = e.pagePath[0];
        repository.load(appId, null, function(application) {
          self.appModel = new LUPAPISTE.ApplicationModel(authorization.create());
          ko.mapping.fromJS(application, {}, self.appModel);

          var attachments = _.filter(ko.mapping.toJS(self.appModel.attachments), function(attachment) {
            return self.appModel.inPostVerdictState() ? postVerdictStates[attachment.applicationState] : !postVerdictStates[attachment.applicationState];
          });

          self.attachments = ko.observableArray(attachmentUtils.getGroupByOperation(attachments, true, self.appModel.allowedAttachmentTypes()));

          self.stampingMode(self.appModel !== null); // show
        });
      } else {
        error("No application ID provided for stamping");
        LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
      }
    } else { // appModel already initialized, show stamping
      self.stampingMode(true);
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
    $("#stamping-container").applyBindings({stampingMode: self.stampingMode, cancelStamping: self.cancelStamping});
  });

  return {
    initStamp: initStamp
  };
})();