LUPAPISTE.IntegrationMessageMonitorModel = function(params) {
  "use strict";
  var self = this;

  self.processing = ko.observable(true);
  self.fileGroups = ko.observable([]);
  self.fileGroupsEmpty = ko.observable(true);

  self.init = function() {
    ajax.query("integration-messages", {id:params.id})
    .processing(self.processing)
      .success(function(resp) {
        self.fileGroups(_.map(["waiting", "error", "ok"], function(group) {
          var files = _.map( _.get( resp, ["messages", group] ),
                             function( f ) {
                               f.href = sprintf("/api/raw/integration-message?id=%s&fileType=%s&filename=%s",
                                                params.id, group, f.name );
                               return f;
                             });
          return {lname: loc(["application.integration-messages", group]),
                  files: _.sortBy(_.flatten(files), "modified").reverse()};
        }));
      self.fileGroupsEmpty = _.every(self.fileGroups(), function (gr) { return _.isEmpty(gr.files); });

    })
    .call();
  };

  // Dialog is initially short, but the content can be quite height after loading the data.
  // Resize the dialog after message lists are rendered.
  self.afterRender = _.partial(hub.send, "resize-dialog");

  self.init();
};

ko.components.register("integration-message-monitor", {viewModel: LUPAPISTE.IntegrationMessageMonitorModel, template: {element: "integration-message-monitor-template"}});
