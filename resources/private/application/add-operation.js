;(function() {
  "use strict";

  var getVisibleApplicationId = pageutil.subPage;
  var model = null;
  var tree = null;

  function Model() {
    var self = this;

    self.title = ko.observable();
    self.url = ko.observable();
    self.operations = ko.observable();
    self.operation = ko.observable();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.waitingOperations = ko.observable();

    self.clear = function() {
      return self.title("").url("").operations(null).operation(null).pending(false).waitingOperations(false);
    };

    self.init = function() {
      self
        .operations(null)
        .operation(null)
        .processing(false)
        .pending(false)
        .waitingOperations(false)
        .title(lupapisteApp.models.application.title())
        .url("#!/application/" + lupapisteApp.models.application.id());

      lupapisteApp.setTitle(lupapisteApp.models.application.title());

      if (lupapisteApp.models.applicationAuthModel.ok("addable-operations")) {
        ajax
          .query("addable-operations", {id: lupapisteApp.models.application.id()})
          .pending(self.waitingOperations)
          .success(function(data) {
            if (lupapisteApp.models.application.id() === getVisibleApplicationId()) {
              self.operations(data.operations);
            }
          })
          .call();
      }
      return self;
    };

    self.addOperation = function(op) {
      ajax
        .command("add-operation", {id: lupapisteApp.models.application.id(), operation: op.op})
        .processing(self.processing)
        .pending(self.pending)
        .success(function() {
          window.location.hash = self.url();
        })
        .call();
        hub.send("track-click", {category:"Application", label:"", event:"addOperation"});
    };

  }
  model = new Model();

  hub.onPageLoad("add-operation", function(e) {
    var newId = e.pagePath[0];
    if (newId !== lupapisteApp.models.application.id()) {
      model.clear();
      repository.load(newId);
    } else {
      model.init();
    }

    if (tree) {
      tree.start();
    }
  });

  hub.subscribe("application-model-updated", function() {
    if (getVisibleApplicationId() === lupapisteApp.models.application.id() && pageutil.getPage() === "add-operation") {
      model.init();
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

    hub.subscribe({eventType: "keyup", keyCode: 37}, tree.back);
    hub.subscribe({eventType: "keyup", keyCode: 33}, tree.start);
    hub.subscribe({eventType: "keyup", keyCode: 36}, tree.start);

  });

})();
