LUPAPISTE.DocgenRepeatingGroupModel = function(params) {
  "use strict";
  var self = this;

  self.service = params.service || lupapisteApp.services.documentDataService;
  self.docModel = params.docModel;

  self.documentId = params.documentId;
  self.applicationId = params.applicationId;
  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.i18npath = params.i18npath;
  self.groupId = ["repeating-group", params.documentId].concat(self.path).join("-");
  self.appendLabel = self.i18npath.concat("_append_label").join(".");
  self.copyLabel = self.i18npath.concat("_copy_label").join(".");
  self.authModel = params.authModel || lupapisteApp.models.applicationAuthModel;
  self.schemaName = params.schema.name;
  self.schemaCss = params.schema.css && params.schema.css.join( " ");

  self.groups = self.service.getInDocument(params.documentId, self.path).model;

  self.indicator = ko.observable().extend({notify: "always"});

  self.groupsRemovable = function(schema) {
    return !_.some(schema.body, "readonly") &&
      !params.isDisabled &&
      self.authModel.ok(self.service.getRemoveCommand(params.documentId));
  };

  self.updatable = function() {
    return self.authModel.ok(self.service.getUpdateCommand(params.documentId));
  };

  function afterRemove(result) {
    if (self.docModel && result.results) {
      self.docModel.showValidationResults(result.results);
    }
  }

  self.removeGroup = function(group) {
    var removeFn = function () {
      self.service.removeRepeatingGroup(params.documentId, params.path, group.index, self.indicator, afterRemove);
    };
    var message = "document.delete." + params.schema.type + ".subGroup.message";
    hub.send("show-dialog", {ltitle: "remove",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: message,
                                               yesFn: removeFn}});
  };

  self.addGroup = function() {
    self.service.addRepeatingGroup(params.documentId, params.path);
  };

  self.duplicateLastGroup = function() {
    var sourceIndex = _(self.groups()).map("index").map(_.parseInt).max();
    self.service.copyRepeatingGroup(params.documentId, params.path, sourceIndex, self.indicator);
  };

  var addOneIfEmpty = function(groups) {
    if ( _.isEmpty(groups) ) {
      self.addGroup();
    }
  };

  self.groups.subscribe(addOneIfEmpty);
  addOneIfEmpty(self.groups());
};
