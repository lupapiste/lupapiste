LUPAPISTE.DocgenInputModel = function(params) {
  "use strict";
  var self = this;

  self.authModel = params.authModel || lupapisteApp.models.applicationAuthModel;

  var service = lupapisteApp.services.documentDataService;

  self.size = uiComponents.sizeClasses[params.schema.size];
  self.schema = params.schema;
  self.path = params.path;
  self.documentId = params.documentId || params.schema.documentId;
  self.model = service.getInDocument(self.documentId, params.path);
  self.value = self.model.model;

  self.i18npath = params.schema.i18nkey ? [params.schema.i18nkey] : params.schema.i18npath;
  if (!self.i18npath) {
    self.i18npath = [util.locKeyFromDocPath([params.schemaI18name].concat(params.path).join("."))];
  }

  self.label = (params.schema.label === false || params.schema.label === "false") ? null : self.i18npath.join(".");

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = self.model.validationResult.extend({notify: "always"});
  self.errorMessage = ko.pureComputed(function() {
    var errType = self.result() && self.result()[0];
    var message = errType && errType !== "tip" && loc(["error", self.result()[1]]);
    return message;
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

  self.classes = ko.computed(function() {
    var classes = [];
    var result = self.result() ? self.result()[0] : undefined;
    if (result) {
      classes.push(result);
    }
    classes.push(self.size);
    return classes.join(" ");
  });

  self.readonly = ko.observable(params.schema.readonly || params.readonly);

  self.disabled = ko.observable(params.isDisabled || !self.authModel.ok(service.getUpdateCommand(self.documentId)) ||
                                util.getIn(params, ["model", "disabled"]));
  var save = function(val) {
    service.updateDoc(self.documentId,
      [[params.path, val]],
      self.indicator);
  };
  self.value.subscribe(_.debounce(save, 500));

  hub.subscribe("document::validation-result", function(results) {
    var res = _.find(results, function(res) {
      return _.isEqual(res.path, self.path);
    });
    self.result(res && res.result);
  });
};
