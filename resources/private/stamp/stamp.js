var stamping = (function() {

  var self = this;

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
    self.stampingMode(self.appModel !== null);
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