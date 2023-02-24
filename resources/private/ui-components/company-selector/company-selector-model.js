LUPAPISTE.CompanySelectorModel = function(params) {
  "use strict";

  var self = this;

  self.indicator = ko.observable();
  self.result = ko.observable();
  self.authorization = params.authModel;

  self.service = lupapisteApp.services.documentDataService;

  self.companies = ko.observableArray(params.companies);
  self.selected = ko.observable(_.isEmpty(params.selected) ? undefined : params.selected);

  self.path = (_.isArray(params.path) ? params.path : params.path.split(".")).concat(params.schema.name);

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
    if (self.authorization.ok("set-company-to-document")) {
      ajax.command("set-company-to-document", p)
      .success(function() {
        function cb() {
          repository.load(params.id);
        }

        self.service.updateDoc(params.documentId,
                               [[self.path, id]],
                               self.indicator,
                               cb);
      })
      .call();
    }
  });
};
