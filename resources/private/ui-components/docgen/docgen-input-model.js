LUPAPISTE.DocgenInputModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.size = uiComponents.sizeClasses[self.params.schema.size];
  self.value = ko.observable(self.params.model ? self.params.model.value : undefined);
  self.path = self.params.path;
  if (!_.isEmpty(self.params.index)) {
    self.path = self.path.concat(self.params.index.toString());
  }
  self.path = self.path.concat(self.params.schema.name);

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = ko.observable().extend({notify: "always"});
  self.errorMessage = ko.observable();

  self.result.subscribe(function(val) {
    var resultMsg = val ? loc(["error", val[1]]) : "";
    self.errorMessage(resultMsg);
  });

  self.showMessagePanel = ko.observable(false);

  self.helpMessage = ko.observable();

  var helpLocKey = util.locKeyFromDocPath(self.params.schemaI18name + "." + self.path.join(".") + ".help");

  if (self.params.schema.i18nkey) {
    helpLocKey = self.params.schema.i18nkey + ".help";
  }

  if (loc.hasTerm(helpLocKey)) {
    self.helpMessage(loc(helpLocKey));
  }

  // set initial validation errors
  var res = _.find(self.params.validationErrors, function(errors) {
    return _.isEqual(errors.path, self.path);
  });

  if (res) {
    self.result(res.result);
  }

  self.classes = ko.computed(function() {
    var classes = [];
    var result = self.result() ? self.result()[0] : undefined;
    if (result) {
      classes.push(result);
    }
    classes.push(self.size);
    return classes.join(" ");
  });

  self.readonly = ko.observable(self.params.schema.readonly || self.params.readonly);

  self.disabled = ko.observable(!lupapisteApp.models.applicationAuthModel.ok("update-doc") ||
                                util.getIn(params, ["model", "disabled"]));
  var save = function(val) {
    uiComponents.save("update-doc",
                       self.params.documentId,
                       self.params.applicationId,
                       self.params.schema.name,
                       self.path,
                       val,
                       self.indicator,
                       self.result);
  };
  self.value.subscribe(_.debounce(save, 500));
};
