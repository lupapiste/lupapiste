LUPAPISTE.TriggerService = function() {
  "use strict";
  var self = this;

  var triggers = ko.observableArray();

  self.processing = ko.observable();
  self.pending = ko.observable();

  self.organizationTaskTriggers = function( organization ) {
    ko.computed( function() {
      triggers( _(util.getIn (organization, ["taskTriggers"])).value());
    });
    return triggers;
  };

  self.addTaskTrigger = function ( triggerData ) {
    var selectedType = ko.unwrap(lupapisteApp.services.triggersTargetService.selected);
    var targets = _.map(selectedType, function(type) {
      return [type["type-group"], type["type-id"]].join('.');
    });
    var handlerObj = null;
    if (triggerData.handler() != undefined) {
      handlerObj = {
        id: triggerData.handler().id(),
        name: {fi: triggerData.handler().name["fi"](),
          sv: triggerData.handler().name["sv"](),
          en: triggerData.handler().name["en"]()}
      };
    }
    var triggerParams = {triggerId: triggerData.id, targets: targets, handler: handlerObj, description: triggerData.description()};
    ajax.command("upsert-task-trigger", triggerParams)
      .processing(self.processing)
      .pending(self.pending)
      .success(function(res) {
        if (triggerData.id != null) {
          triggers.remove (function (trigger) {return trigger.id === triggerData.id;});
        }
        triggers.push(res.trigger);
        LUPAPISTE.ModalDialog.close();
        hub.send("indicator", {style: "positive"});
      })
      .error(function(e) {
        LUPAPISTE.ModalDialog.close();
        hub.send("indicator", {style: "negative", message: e.text});
      })
      .call();
  };


  self.removeTaskTrigger = function (triggerId) {
    triggers.remove (function (trigger) {return trigger.id === triggerId;});
    ajax.command( "remove-task-trigger", {triggerId: triggerId })
      .success(function() {
        hub.send("indicator", {style: "positive"});
      })
      .error(function(e) {
        hub.send("indicator", {style: "negative", message: e.text});
      })
      .call();
  };
};