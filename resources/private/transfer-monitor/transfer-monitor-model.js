LUPAPISTE.TransferMonitorModel = function() {
  "use strict";
  var self = this;

  self.processing = ko.observable(true);
  self.waiting = ko.observable([]);
  self.ok = ko.observable([]);
  self.error = ko.observable([]);

  self.init = function() {
    var id = lupapisteApp.models.application.id();
    ajax.query("transfers", {id:id})
    .processing(self.processing)
    .success(function(resp) {
      self.waiting(_.sortBy(_.concat(resp.krysp.waiting, resp.ah.waiting), "modified").reverse());
      setTimeout(_.partial(hub.send, "resize-dialog"), 100); // FIXME after-render
    })
    .call();
  };

  self.init();
};

ko.components.register("transfer-monitor", {viewModel: LUPAPISTE.TransferMonitorModel, template: {element: "transfer-monitor-template"}});
