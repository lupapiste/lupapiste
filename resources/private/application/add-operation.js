;(function() {

  function Model() {
    var self = this;
    
    self.application = null;

    self.title = ko.observable();
    self.url = ko.observable();
    self.operation = ko.observable();
    self.treeReady = ko.observable(false);
    self.tree = null;
    
    self.clear = function() {
      self.application = null;
      return self.title("").url("").operation(null).treeReady(false);
    };
    
    self.init = function(application) {
      self.application = application;
      self
        .title(application.title)
        .url("#!/application/" + application.id)
        .operation(null)
        .treeReady(false);
      var id = application.id;
      ajax
        .query("municipality", {municipality: application.municipality})
        .success(function (data) {
          if (self.application.id === id) self.setOperations(data.operations).treeReady(true);
        })
        .call();
      return self;
    };
    
    self.setOperations = function(operations) {
      if (self.tree) self.tree.reset(operations);
      return self;
    };
    
    self.generateLast = function(value) {
      var e = document.createElement("div");
      e.setAttribute("class", "tree-result");
      
      var btn = document.createElement("button");
      btn.setAttribute("class", "btn btn-primary");
      btn.innerText = loc('addOperation');
      btn.onclick = function() {
        ajax.command("add-operation", {id: self.application.id, operation: value})
          .success(function() {
            window.location.hash = self.url();
          })
          .call();
        return false;
      };
      e.appendChild(btn);
      
      btn = document.createElement("button");
      btn.setAttribute("class", "btn");
      btn.innerText = loc('back');
      btn.onclick = function() {
        self.tree.goback();
        return false;
      };
      e.appendChild(btn);
      
      return e;
    };
  }
    
  var model = new Model();
  var currentId;

  hub.onPageChange("add-operation", function(e) {
    var newId = e.pagePath[0];
    if (newId !== currentId) {
      currentId = newId;
      model.clear();
      hub.send("load-application", {id: currentId});
    }
  });

  hub.subscribe("application-loaded", function(e) {
    if (currentId === e.applicationDetails.application.id) {
      model.init(e.applicationDetails.application);
    }
  });

  $(function() {
    var tree = selectionTree.create(
        $("#add-operation .tree-content"),
        $("#add-operation .tree-breadcrumbs"),
        model.operation,
        model.generateLast);
    model.tree = tree;
    ko.applyBindings(model, $("#add-operation")[0]);
  });
  
})();
