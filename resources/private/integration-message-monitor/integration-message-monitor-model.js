LUPAPISTE.IntegrationMessageMonitorModel = function(params) {
  "use strict";
  var self = this;

  self.processing = ko.observable(true);
  self.fileGroups = ko.observable([]);

  self.init = function() {
    ajax.query("integration-messages", {id:params.id})
    .processing(self.processing)
    .success(function(resp) {
      self.fileGroups(_.map(["waiting", "error", "ok"], function(group) {
        var files = _.map(["krysp","ah"], function(t) {
          return _.map(resp[t][group], function(f) {
            if (group !== "waiting") {
              f.href = _.sprintf("/api/raw/integration-message?id=%s&transferType=%s&fileType=%s&filename=%s", params.id, t, group, f.name);
            }
            return f;
          });
        });
        return {lname: loc(["application.integration-messages", group]),
                files: _.sortBy(_.flatten(files), "modified").reverse()};
      }));
    })
    .call();
  };

  // Dialog is initially short, but the content can be quite height after loading the data.
  // Resize the dialog after message lists are rendered.
  self.afterRender = _.partial(hub.send, "resize-dialog");

  self.init();
};

ko.components.register("integration-message-monitor", {viewModel: LUPAPISTE.IntegrationMessageMonitorModel, template: {element: "integration-message-monitor-template"}});
