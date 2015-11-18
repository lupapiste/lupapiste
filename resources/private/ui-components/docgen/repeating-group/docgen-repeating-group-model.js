LUPAPISTE.DocgenRepeatingGroupModel = function(params) {
  "use strict";
  var self = this;

  // self.groups = ko.observableArray();

  self.params = params;

  self.service = lupapisteApp.services.documentDataService;

  self.documentId = params.documentId;
  self.applicationId = params.applicationId;
  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.i18npath = params.i18npath;
  self.groupId = ["repeating-group", params.documentId].concat(self.path).join("-");
  self.appendLabel = params.i18npath.concat("_append_label").join(".");
  self.copyLabel = params.i18npath.concat("_copy_label").join(".");

  self.groups = self.service.getInDocument(params.documentId, self.path).model;

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = ko.observable().extend({notify: "always"});
  self.errorMessage = ko.observable();

  self.result.subscribe(function(val) {
    var resultMsg = val ? loc(["error", val[1]]) : "";
    self.errorMessage(resultMsg);
  });

  self.groupsRemovable = function() {
    return lupapisteApp.models.applicationAuthModel.ok(self.service.getRemoveCommand(self.params.documentId));
  };

  self.updatable = function() {
    return lupapisteApp.models.applicationAuthModel.ok(self.service.getUpdateCommand(self.params.documentId));
  };

  self.removeGroup = function(group) {
    var path = self.params.path.concat(group.index);

    var removeFn = function () {
      self.service.removeRepeatingGroup(self.params.documentId, self.params.path, group.index, self.indicator, self.result);
    };

    var message = "document.delete." + params.schema.type + ".subGroup.message";

    hub.send("show-dialog", {ltitle: "document.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: message,
                                               yesFn: removeFn}});
  };

  self.addGroup = function() {
    self.service.addRepeatingGroup(self.params.documentId, self.params.path);
  };

  self.duplicateLastGroup = function() {
    var sourceIndex = _.parseInt( _(self.groups()).map("index").max() );
    self.service.copyRepeatingGroup(self.params.documentId, self.params.path, sourceIndex, self.indicator, self.result);
  };

  var addOneIfEmpty = function(groups) {
    if ( _.isEmpty(groups) ) {
      self.addGroup();
    }
  };

  self.groups.subscribe(addOneIfEmpty);
  addOneIfEmpty(self.groups());
};
