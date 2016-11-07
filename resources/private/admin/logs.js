;(function() {
  "use strict";

  function LogsModel() {
    var self = this;

    self.debug = ko.observable([]);
    self.info = ko.observable([]);
    self.warn = ko.observable([]);
    self.error = ko.observable([]);
    self.fatal = ko.observable([]);

    self.load = function() {
      ajax
        .query("frontend-log-entries")
        .success(function(resp) {
          _.each(resp.log, function(v, k) {
            self[k](v);
          });
        })
        .call();
    };

    self.resetLog = function() {
      ajax
        .command("reset-frontend-log")
        .success(self.load)
        .call();
    };
  }

  var logsModel = new LogsModel();

  hub.onPageLoad("logs", logsModel.load);

  $(function() {
    $("#logs").applyBindings(logsModel);
  });

})();
