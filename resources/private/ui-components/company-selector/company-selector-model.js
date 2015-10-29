LUPAPISTE.CompanySelectorModel = function(params) {
  "use strict";

  var self = this;

  self.indicator = ko.observable();
  self.result = ko.observable();
  self.authorization = params.authModel;

  self.companies = ko.observableArray(params.companies);
  self.selected = ko.observable(_.isEmpty(params.selected) ? undefined : params.selected);

  self.setOptionDisable = function(option, item) {
    if (!item) {
      return;
    }
    ko.applyBindingsToNode(option, {disable: item.disable}, item);
  };

  self.selected.subscribe(function(id) {
    var p = {
      id: params.id,
      documentId: params.documentId,
      companyId: id ? id : "",
      path: params.path
    };
    ajax.command("set-company-to-document", p)
    .success(function() {
      function cb() {
        repository.load(params.id);
      }

      uiComponents.save("update-doc",
                         params.documentId,
                         params.id,
                         params.schema.name,
                         params.path.split(".").concat([params.schema.name]),
                         id,
                         self.indicator,
                         self.result,
                         cb);
    })
    .call();
  });
};
