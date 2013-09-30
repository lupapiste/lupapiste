;(function() {
  "use strict";

  function ActionsModel() {
    var self = this;

    self.actions = ko.observable();

    self.load = function() {
      ajax
        .query("actions")
        .success(function(d) { self.actions(_.values(d.actions)); })
        .call();
    };
  }

  var actionsModel = new ActionsModel();

  hub.onPageChange("actions", actionsModel.load);

  $(function() {
    $("#actions").applyBindings(actionsModel);
  });

})();
