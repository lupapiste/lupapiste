;(function() {
  "use strict";

  var currentId = null;
  var model = null;
  var tree = null;

  function Model() {
    var self = this;

    self.application = null;

    self.title = ko.observable();
    self.url = ko.observable();
    self.operations = ko.observable();
    self.operation = ko.observable();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.waitingOperations = ko.observable();

    self.clear = function() {
      self.application = null;
      return self.title("").url("").operations(null).operation(null).pending(false).waitingOperations(false);
    };

    self.init = function(application) {
      self.application = application;
      self
        .operations(null)
        .operation(null)
        .processing(false)
        .pending(false)
        .waitingOperations(false)
        .title(application.title)
        .url("#!/application/" + application.id);
      var id = application.id;
      ajax
        .query("operations", {permitType: application.permitType})
        .pending(self.waitingOperations)
        .success(function (data) {
          if (self.application.id === id) {
            self.operations(data.operations);
          }
        })
        .call();
      return self;
    };

    self.addOperation = function(op) {
      ajax
        .command("add-operation", {id: self.application.id, operation: op.op})
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
          window.location.hash = self.url();
        })
        .call();
    };

  }
  model = new Model();

  hub.onPageChange("add-operation", function(e) {
    var newId = e.pagePath[0];
    if (newId !== currentId) {
      currentId = newId;
      model.clear();
      repository.load(currentId);
    }
    if (tree) {
      tree.start();
    }
  });

  repository.loaded(["add-operation"], function(application) {
    if (currentId === application.id) {
      model.init(application);
    }
  });

  $(function() {

    $("#add-operation").applyBindings(model);

    tree = $("#add-operation .operation-tree").selectTree({
      template: $("#create-templates"),
      last: $("#add-operation-templates .tree-last"),
      onSelect: function(v) { model.operation(v ? v.op : null); },
      baseModel: model
    });

    function operations2tree(e) {
      var key = e[0], value = e[1];
      return [{op: key}, _.isArray(value) ? _.map(value, operations2tree) : {op: value}];
    }

    model.operations.subscribe(function(v) {
      tree.reset(_.map(v, operations2tree));
    });

    hub.subscribe({type: "keyup", keyCode: 37}, tree.back);
    hub.subscribe({type: "keyup", keyCode: 33}, tree.start);
    hub.subscribe({type: "keyup", keyCode: 36}, tree.start);

  });

})();
