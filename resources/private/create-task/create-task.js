/**
 * Controller for create task dialog.
 * Singleton, suppress warning about "weird" construction.
 */
// jshint supernew:true
LUPAPISTE.createTaskController = new (function() {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-create-task";

  self.id = 0;
  self.source = {};
  self.taskName = ko.observable(null);
  self.taskType = ko.observable(null);
  self.taskSubtype = ko.observable(null);
  self.taskTypes = ko.observable([]);

  self.taskSubtypes = ko.computed(function() {
    var currentTask = _.find(self.taskTypes(), function(task) {
      return task.id === self.taskType();
    });
    return currentTask && currentTask.subTypes;
  });

  self.subtypeNeeded = ko.pureComputed(function() {
    var subtypes = self.taskSubtypes();
    return subtypes && subtypes.length > 0;
  });


  self.enabled = ko.computed(function() {
    return self.taskType() && self.taskName() && (self.subtypeNeeded() ? self.taskSubtype() : true);
  });

  self.reset = function(id, source) {
    self.id = id;
    self.source = source;
    self.taskName(null);
    self.taskType(null);
    self.taskTypes([]);
    self.taskSubtype(null);
  };

  // Open the dialog
  self.createTask = function(app) {
    if (typeof app.id === "function") {
      self.reset(app.id(), self.source);
    }

    ajax.query("task-types-for-application", {id: self.id,  lang: loc.getCurrentLanguage()})
      .success(function(data) {
        self.taskTypes(_.map(data.schemas, function(schema) {
          return {id:       schema.schemaName,
                  text:     loc([schema.schemaName, "_group_label"]),
                  subTypes: schema.types};
        }));
        LUPAPISTE.ModalDialog.open(self.dialogSelector);
      }).call();
  };

  self.save = function() {
    ajax.command("create-task", {id: self.id,
                                 taskName: self.taskName(),
                                 schemaName: self.taskType(),
                                 taskSubtype: self.taskSubtype(),
                                 source: self.source})
      .success(function() {
        repository.load(self.id);
        LUPAPISTE.ModalDialog.close();
      })
      .call();
  };

  $(function() {
    $(self.dialogSelector).applyBindings({createTask: self});
  });

})();
