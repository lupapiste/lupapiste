;(function() {
  "use strict";

  function ActionsModel() {
    var self = this;

    self.actions = ko.observable();
    self.uncalledActions = ko.observable();
    self.uncalledActionsCount = ko.observable(-1);

    self.load = function() {
      ajax
        .query("actions")
        .success(function(d) { self.actions(_.values(d.actions).filter(function(a){return a.type && a.name;})); })
        .call();

      ajax.get("/system/action-counters").success(function(e){
        var uncalled = _.pickBy(e, function(c) {return c === 0;});
        var keys = _.keys(uncalled);
        self.uncalledActionsCount(keys.length);
        self.uncalledActions(keys.join(", "));
        }).raw(true).call();

    };
  }

  var actionsModel = new ActionsModel();

  hub.onPageLoad("actions", actionsModel.load);

  $(function() {
    $("#actions").applyBindings(actionsModel);
  });

})();
