LUPAPISTE.createTaskController = new (function() {
  "use strict";
  var self = this;
  self.dialogSelector = "#dialog-create-task";

  self.id = 0;
  self.source = {};
  self.taskName = ko.observable(null);
  self.taskType = ko.observable(null);
  self.taskTypes = ko.observable([]);
  self.disabled = ko.computed(function() {
    return !self.taskType() || !self.taskName();
  });

  self.reset = function(id, source) {
    self.id = id;
    self.source = source;
    self.taskName(null);
    self.taskType(null);
    self.taskTypes([]);
  };

  // Open the dialog
  self.createTask = function(app) {
    if (typeof app.id === "function") {
      self.reset(app.id(), self.source);
    }

    ajax.query("task-types-for-application", {id: self.id})
      .success(function(data) {
        self.taskTypes(_.map(data.schemas, function(id) { return {id: id, text: loc([id, "_group_label"])};}));
        LUPAPISTE.ModalDialog.open(self.dialogSelector);
      }).call();
  };

  self.save = function() {
    ajax.command("create-task", {id: self.id, taskName: self.taskName(), schemaName: self.taskType(), source: self.source})
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
