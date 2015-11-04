LUPAPISTE.DocgenInputModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.authModel = params.authModel || lupapisteApp.models.applicationAuthModel;

  self.size = uiComponents.sizeClasses[self.params.schema.size];
  //self.value = ko.observable(self.params.model ? self.params.model.value : undefined);
  self.path = self.params.path;
  self.documentId = self.params.documentId || self.params.schema.documentId;
  self.value = lupapisteApp.services.documentDataService.getInDocument(self.documentId, self.path).model;
  
  self.i18npath = self.params.schema.i18nkey ? [self.params.schema.i18nkey] : self.params.schema.i18npath;
  if (!self.i18npath) {
    self.i18npath = [util.locKeyFromDocPath([self.params.schemaI18name].concat(self.params.path).join("."))];
  }

  self.label = (params.schema.label === false || params.schema.label === "false") ? null : self.i18npath.join(".");

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = ko.observable().extend({notify: "always"});
  self.errorMessage = ko.observable();

  self.result.subscribe(function(val) {
    var resultMsg = val ? loc(["error", val[1]]) : "";
    self.errorMessage(resultMsg);
  });

  self.showMessagePanel = ko.observable(false);
  self.events = {
    mouseover: function() { self.showMessagePanel(true); },
    mouseout: function() { self.showMessagePanel(false); }
  };

  self.helpMessage = ko.observable();

  var helpLocKey = self.i18npath.concat("help").join(".");

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

  self.disabled = ko.observable(params.isDisabled || !self.authModel.ok("update-doc") ||
                                util.getIn(params, ["model", "disabled"]));
  var save = function(val) {
    uiComponents.save("update-doc",
                       self.documentId,
                       self.params.applicationId,
                       self.params.schema.name,
                       self.path,
                       val,
                       self.indicator,
                       self.result);
  };
  self.value.subscribe(_.debounce(save, 500));
};
