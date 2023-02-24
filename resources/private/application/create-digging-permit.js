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
    self.organization = ko.observable();
    self.processing = ko.observable();
    self.pending = ko.observable();
    self.waitingOperations = ko.observable();
    self.lastPageParams = self;

    self.clear = function() {
      return self.title("").url("").operations(null).operation(null).pending(false).waitingOperations(false);
    };

    self.cancel = function() {
      pageutil.openPage( "application", lupapisteApp.models.application.id() );
    };

    self.init = function() {
      self
        .operations(null)
        .operation(null)
        .processing(false)
        .pending(false)
        .organization(lupapisteApp.models.application.organization())
        .title(lupapisteApp.models.application.title());

      lupapisteApp.setTitle(lupapisteApp.models.application.title());

      if (lupapisteApp.models.applicationAuthModel.ok("selected-digging-operations-for-organization")) {
        ajax
          .query("selected-digging-operations-for-organization", {id: lupapisteApp.models.application.id(),
                                                                  organization: self.organization()})
          .pending(self.pending)
          .success(function(data) {
            if (lupapisteApp.models.application.id() === getVisibleApplicationId()) {
              self.operations(_.map( data.operations, operations2tree));
            }
          })
          .error(function(data) {
            notify.ajaxError(data);
          })
          .call();
      }
      return self;
    };

    self.createDiggingPermit = function() {
      ajax
        .command("create-digging-permit", {id: lupapisteApp.models.application.id(),
                                           operation: self.operation()})
        .processing(self.processing)
        .pending(self.pending)
        .success(function(resp) {
          // TODO route to newly created application
          pageutil.openApplicationPage({id: resp.id});
          hub.send("indicator", {style: "positive",
                                 message: "application.createDiggingPermit.success",
                                 sticky: true});

        })
        .error(function(data) {
          notify.ajaxError(data);
        })
        .call();
    };

  }
  model = new Model();

  hub.onPageLoad("create-digging-permit", function(e) {
    var newId = e.pagePath[0];
    if (newId !== lupapisteApp.models.application.id()) {
      model.clear();
      repository.load(newId);
    } else {
      model.init();
    }

  });

  hub.subscribe("application-model-updated", function() {
    if (getVisibleApplicationId() === lupapisteApp.models.application.id() && pageutil.getPage() === "create-digging-permit") {
      model.init();
    }
  });

  $(function() {

    $("#create-digging-permit").applyBindings(model);
  });

})();
