LUPAPISTE.OrganizationNameEditorModel = function() {
  "use strict";

  var self = this;

  self.id = ko.observable();
  self.names = ko.observableArray([]);

  function unWrapName(nameObj) {
    return _.set({}, nameObj.lang, util.getIn(nameObj, ["name"]));
  }

  function saveName(nameObj) {
    ajax.command("update-organization-name", {"org-id": self.id(),
                                              "name": unWrapName(nameObj)})
      .success(util.showSavedIndicator)
      .error(util.showSavedIndicator)
      .call();
  }

  function wrapName(name, lang) {
    var nameObj = {
      lang: lang,
      name: ko.observable(name),
      indicator: ko.observable().extend({notify: "always"})
    };
    nameObj.name.subscribe(function() {
      _.debounce(saveName, 500)(nameObj);
    });
    return nameObj;
  }

  function queryNames() {
    ajax.query("organization-name-by-user")
      .success(function(res) {
        self.id(util.getIn(res, ["id"]));
        self.names(_.map(util.getIn(res, ["name"]), wrapName));
      })
      .call();
  }

  queryNames();
};
