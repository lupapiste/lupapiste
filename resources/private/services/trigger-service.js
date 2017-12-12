LUPAPISTE.TriggerService = function() {
  "use strict";
  var self = this;

  var triggers = ko.observableArray();

  self.processing = ko.observable();
  self.pending = ko.observable();

  self.canEdit = ko.computed( _.wrap(  "upsert-assignment-trigger",
                                       lupapisteApp.models.globalAuthModel.ok ));

  self.organizationAssignmentTriggers = function(organization) {
    ko.computed(function() {
      triggers(_(util.getIn (organization, ["assignmentTriggers"])).value());
    });
    return triggers;
  };

  function wrapHandlerInObject (triggerData) {
    var handlerObj = {
      id: null,
      name: null
    };
    if (triggerData.handler() !== undefined) {
      handlerObj = {
        id: triggerData.handler().id(),
        name: {fi: triggerData.handler().name.fi(),
               sv: triggerData.handler().name.sv(),
               en: triggerData.handler().name.en()}
      };
    }
    return handlerObj;
  }

  function wrapTriggerParameters(triggerData) {
    var selectedType = ko.unwrap(lupapisteApp.services.triggersTargetService.selected);
    var targets = _.map(selectedType, function(type) {
      return [type["type-group"], type["type-id"]].join(".");
    });
    return {triggerId: triggerData.id,
            targets: targets,
            handler: wrapHandlerInObject(triggerData),
            description: triggerData.description()};
  }

  self.addAssignmentTrigger = function ( triggerData ) {
    ajax.command("upsert-assignment-trigger", wrapTriggerParameters(triggerData))
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


  self.removeAssignmentTrigger = function (triggerId) {
    triggers.remove (function (trigger) {return trigger.id === triggerId;});
    ajax.command( "remove-assignment-trigger", {triggerId: triggerId })
      .success(function() {
        hub.send("indicator", {style: "positive"});
      })
      .error(function(e) {
        hub.send("indicator", {style: "negative", message: e.text});
      })
      .call();
  };
};
