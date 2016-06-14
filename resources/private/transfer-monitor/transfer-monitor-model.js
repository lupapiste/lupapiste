LUPAPISTE.TransferMonitorModel = function(params) {
  "use strict";
  var self = this;

  self.processing = ko.observable(true);
  self.fileGroups = ko.observable([]);

  self.init = function() {
    ajax.query("transfers", {id:params.id})
    .processing(self.processing)
    .success(function(resp) {
      self.fileGroups(_.map(["waiting", "error", "ok"], function(group) {
        return {lname: loc(["application.transfers", group]),
                files: _.sortBy(_.concat(resp.krysp[group], resp.ah[group]), "modified").reverse()};
      }));
    })
    .call();
  };

  self.init();
};

ko.components.register("transfer-monitor", {viewModel: LUPAPISTE.TransferMonitorModel, template: {element: "transfer-monitor-template"}});
