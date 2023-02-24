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
    self.oldOperation = ko.observable();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.error = ko.observable();

    self.lastPageParams = self;

    function returnToApplication() {
      pageutil.openPage( "application", lupapisteApp.models.application.id() );
    }

    self.cancel = returnToApplication;

    self.clear = function() {
      return self.title("").url("").operations(null).operation(null).pending(false);
    };

    self.init = function() {
      self
        .operations(null)
        .operation(null)
        .oldOperation(pageutil.getPagePath()[1])
        .processing(false)
        .pending(false)
        .error( null )
        .title(lupapisteApp.models.application.title());

      lupapisteApp.setTitle(lupapisteApp.models.application.title());

      if (lupapisteApp.models.applicationAuthModel.ok("addable-operations")) {
        ajax
          .query("addable-operations", {id: lupapisteApp.models.application.id()})
          .pending(self.pending)
          .success(function(data) {
            if (lupapisteApp.models.application.id() === getVisibleApplicationId()) {
              self.operations(_.map(data.operations, operations2tree));
              self.error( _.isEmpty( data.operations )
                          ? loc( "operations.replaceOperationNotPossible")
                          : null );
            }
          })
          .call();
      }
      return self;
    };

    self.replaceOperation = function() {
      ajax
        .command("replace-operation", {id: lupapisteApp.models.application.id(),
                                       opId: self.oldOperation(),
                                       operation: self.operation()})
        .processing(self.processing)
        .pending(self.pending)
        .success( returnToApplication)
        .call();
    };

  }
  model = new Model();

  hub.onPageLoad("replace-operation", function(e) {
    var newId = e.pagePath[0];
    if (newId !== lupapisteApp.models.application.id()) {
      model.clear();
      repository.load(newId);
    } else {
      model.init();
    }
  });

  hub.subscribe("application-model-updated", function() {
    if (getVisibleApplicationId() === lupapisteApp.models.application.id() && pageutil.getPage() === "replace-operation") {
      model.init();
    }
  });

  $(function() {

    $("#replace-operation").applyBindings(model);

  });

})();
