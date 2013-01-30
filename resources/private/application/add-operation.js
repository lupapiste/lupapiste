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
      self.operation(null).treeReady(false).title(application.title).url("#!/application/" + application.id);
      var id = application.id;
      ajax
        .query("municipality", {municipality: application.municipality})
        .success(function (data) { if (self.application.id === id) self.setOperations(data.operations).treeReady(true); })
        .call();
      return self;
    };

    self.setOperations = function(operations) {
      if (self.tree) self.tree.reset(operations);
      return self;
    };

    self.generateLast = function(val, key) {
      var e = $("<div>").addClass("tree-magic");
      e.append($("<a>")
        .addClass("tree-action")
        .html(loc('addOperation'))
        .click(function(e) {
          ajax
            .command("add-operation", {id: self.application.id, operation: val.op})
            .success(function() { window.location.hash = self.url(); })
            .call();
          var target = $(e.target);
          setTimeout(function() {
              target
                .parent()
                .empty()
                .append($("<img>").attr("src", "/img/ajax-loader.gif"))
            }, 200);
          return false;
        }));
      var icon = $("<span>").addClass("font-icon icon-tree-back");
      e.append($("<a>")
        .addClass("tree-back")
        .html(loc('back'))
        .append(icon)
        .click(self.tree.goback));
      return e[0];
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
    var application = e.applicationDetails.application;
    if (currentId === application.id) model.init(application);
  });

  $(function() {
    var tree = selectionTree.create(
        $("#add-operation .tree-content"),
        $("#add-operation .tree-breadcrumbs"),
        model.operation,
        model.generateLast,
        "operations");
    model.tree = tree;
    ko.applyBindings(model, $("#add-operation")[0]);
  });

})();
