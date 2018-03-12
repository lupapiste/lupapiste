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
        self.oldOperation = ko.observable();
        self.processing = ko.observable();
        self.pending = ko.observable();
        self.waitingOperations = ko.observable();
        self.showOperations = ko.observable();

        self.clear = function() {
            return self.title("").url("").operations(null).operation(null).pending(false).waitingOperations(false);
        };

        self.init = function() {
            self
                .operations(null)
                .operation(null)
                .oldOperation(pageutil.getPagePath()[1])
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
                            self.showOperations(!_.isEmpty(self.operations()));
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
                .success(function() {
                    window.location.hash = self.url();
                })
                .call();
            hub.send("track-click", {category:"Application", label:"", event:"replaceOperation"});
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

        if (tree) {
            tree.start();
        }
    });

    hub.subscribe("application-model-updated", function() {
        if (getVisibleApplicationId() === lupapisteApp.models.application.id() && pageutil.getPage() === "replace-operation") {
            model.init();
        }
    });

    $(function() {

        $("#replace-operation").applyBindings(model);

        tree = $("#replace-operation .operation-tree").selectTree({
            template: $("#create-templates"),
            last: $("#replace-operation-templates .tree-last"),
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
