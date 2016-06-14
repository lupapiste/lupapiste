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
        var files = _.map(["krysp","ah"], function(t) {
          return _.map(resp[t][group], function(f) {
            f.href = _.sprintf("/api/raw/transfer?id=%s&transferType=%s&fileType=%s&filename=%s", params.id, t, group, f.name);
            return f;
          });
        });
        return {lname: loc(["application.transfers", group]),
                files: _.sortBy(_.flatten(files), "modified").reverse()};
      }));
    })
    .call();
  };

  self.init();
};

ko.components.register("transfer-monitor", {viewModel: LUPAPISTE.TransferMonitorModel, template: {element: "transfer-monitor-template"}});
