LUPAPISTE.DocgenRepeatingGroupModel = function(params) {
  "use strict";
  var self = this;

  self.groups = ko.observableArray();

  self.params = params;

  self.path = _.isArray(params.path) ? params.path : [params.path];
  self.groupId = ["repeating-group", params.documentId].concat(self.path).join("-");
  self.appendLabel = params.i18npath.concat("_append_label").join(".");
  self.copyLabel = params.i18npath.concat("_copy_label").join(".");

  self.indicator = ko.observable().extend({notify: "always"});
  self.result = ko.observable().extend({notify: "always"});

  var createGroup = function(groupModel, index) {
    return _.extend({}, self.params, {
      index: index,
      path: self.path.concat(index),
      model: groupModel,
    });
  }

  self.removeGroup = function(group) {
    var path = self.params.path.concat(group.index);

    var cb = function () {
      var g = _.find(self.groups(), function(g) {
        return g.index === group.index;
      });
      self.groups.remove(g);
    };

    var removeFn = function () {
      uiComponents.removeRow(self.params.documentId, self.params.applicationId, path, self.indicator, self.result, cb);
    };

    var message = "document.delete." + params.schema.type + ".subGroup.message";

    hub.send("show-dialog", {ltitle: "document.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: message,
                                               yesFn: removeFn}});
  }

  self.addGroup = function() {
    var dataIndex = parseInt( _(self.groups()).map('index').max() ) + 1;
    self.groups.push(createGroup({}, dataIndex || 0));
  }

  self.duplicateLastGroup = function() {
    var sourceIndex = parseInt( _(self.groups()).map('index').max() );
    uiComponents.copyRow(self.params.documentId,
                         self.params.applicationId,
                         self.path,
                         sourceIndex,
                         sourceIndex + 1,
                         self.indicator,
                         self.result);
  };

  self.groups(_.map(params.model, createGroup));

  var addOneIfEmpty = function(groups) {
    if ( _.isEmpty(groups) ) {
      self.addGroup();
    }
  }

  self.groups.subscribe(addOneIfEmpty);
  addOneIfEmpty(self.groups());
};