LUPAPISTE.OrganizationNameEditorModel = function() {
  "use strict";

  var self = this;

  self.id = ko.observable();
  self.names = ko.observableArray([]);

  function wrapName(name, lang) {
    return {lang: lang, name: ko.observable(name)};
  }

  function unWrapName(o) {
    return [o.lang, util.getIn(o, ["name"])];
  }

  function queryNames() {
    ajax.query("organization-name-by-user")
      .success(function(res) {
        self.id(util.getIn(res, ["id"]));
        self.names(_.map(util.getIn(res, ["name"]), wrapName));
      })
      .call();
  }

  self.updateOrganizationName = function() {
    var names = _(self.names()).map(unWrapName).fromPairs().value();
    ajax.command("update-organization-name", {"org-id": self.id(),
                                              "name": names})
      .success(util.showSavedIndicator)
      .call();
  };

  queryNames();
};
