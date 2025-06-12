;(function() {
  "use strict";

  var getVisibleApplicationId = pageutil.subPage;
  var model = null;

  function operations2tree(e) {
    var key = e[0], value = e[1];
    return [{op: key}, _.isArray(value) ? _.map(value, operations2tree) : {op: value}];
  }

  function Model() {
    var self = this;

    self.title = ko.observable();
    self.operations = ko.observable();
    self.operation = ko.observable();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.lastPageParams = self;

    function returnToApplication() {
      pageutil.openPage( "application", lupapisteApp.models.application.id() );
    }

    self.cancel = returnToApplication;

    self.clear = function() {
      return self.title("").url("").operations(null).operation(null).pending(false).waitingOperations(false);
    };

    self.init = function() {
      self
        .operations(null)
        .operation(null)
        .processing(false)
        .pending(false)
        .title(lupapisteApp.models.application.title());

      lupapisteApp.setTitle(lupapisteApp.models.application.title());

      if (lupapisteApp.models.applicationAuthModel.ok("addable-operations")) {
        ajax
          .query("addable-operations", {id: lupapisteApp.models.application.id()})
          .pending(self.pending)
          .success(function(data) {
            if (lupapisteApp.models.application.id() === getVisibleApplicationId()) {
              self.operations( _.map( data.operations, operations2tree));
            }
          })
          .call();
      }
      return self;
    };

    self.addOperation = function() {
      ajax
        .command("add-operation", {id: lupapisteApp.models.application.id(),
                                   operation: self.operation()})
        .processing(self.processing)
        .pending(self.pending)
        .success( returnToApplication )
        .call();
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
  });

  hub.subscribe("application-model-updated", function() {
    if (getVisibleApplicationId() === lupapisteApp.models.application.id() && pageutil.getPage() === "add-operation") {
      model.init();
    }
  });

  $(function() {

    $("#add-operation").applyBindings(model);

  });

})();
