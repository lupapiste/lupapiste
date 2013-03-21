;(function() {

  function Model() {
    var self = this;

    self.application = null;

    self.title = ko.observable();
    self.url = ko.observable();
    self.operations = ko.observable();
    self.operation = ko.observable();
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
        .pending(false)
        .waitingOperations(false)
        .title(application.title)
        .url("#!/application/" + application.id);
      var id = application.id;
      var handle = setTimeout(_.partial(self.waitingOperations, true), 100);
      ajax
        .query("municipality", {municipality: application.municipality})
        .success(function (data) {
          if (self.application.id === id) {
            clearTimeout(handle);
            self.waitingOperations(false).operations(data.operations);
          }
        })
        .call();
      return self;
    };

    self.addOperation = function(op) {
      var handle = setTimeout(_.partial(self.pending, true), 100);
      ajax
        .command("add-operation", {id: self.application.id, operation: op.op})
        .success(function() {
          clearTimeout(handle);
          self.pending(false);
          window.location.hash = self.url();
        })
        .call();
    };
    
  }

  var model = new Model();
  var currentId;

  hub.onPageChange("add-operation", function(e) {
    var newId = e.pagePath[0];
    if (newId !== currentId) {
      currentId = newId;
      model.clear();
      repository.load(currentId);
    }
  });

  repository.loaded(function(e) {
    var application = e.applicationDetails.application;
    if (currentId === application.id) { model.init(application); }
  });

  $(function() {

    $("#add-operation").applyBindings(model);

    var tree = $("#add-operation .operation-tree").selectTree({
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
