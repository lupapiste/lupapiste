LUPAPISTE.DocgenHuoneistotTableModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenRepeatingGroupModel(params));

  self.componentTemplate = (params.template || params.schema.template) || "default-docgen-table-template";

  self.schema = params.schema;

  self.groupId = ["table", params.documentId].concat(self.path).join("-");
  self.groupLabel = params.i18npath.concat("_group_label").join(".");
  self.groupHelp = params.schema["group-help"];

  self.authModel = params.authModel;

  self.columnHeaders = _.map(self.schema.body, function(schema) {
    return {
      name: params.i18npath.concat(schema.name),
      required: !!schema.required
    };
  });
  self.columnHeaders.push({
    name: self.groupsRemovable(self.schema) ? "remove" : "",
    required: false
  });

  self.disabled = ko.observable(false);
  self.readonly = ko.observable(false);
  self.showMessagePanel = ko.observable(false);
  self.errorMessage = [];

  self.selectOptionsTextFn = function(colName) {
    return function(item) {return loc(item.i18nkey || ["huoneistot", colName, item.name].join("."));};
  };

  function indicator(evt) {
    if (evt.type === "err") {
      hub.send("indicator", {style: "negative", message: "form.err"});
    }
  }

  function save(item) {
    console.log(item);
    if (item.model() != null) {
      self.service.updateDoc(self.documentId, [[item.path, item.model()]], indicator);
    }
  }

  function cellInfo(doc, item) {
    var result = ko.pureComputed(function() {
      var validation = _.find(doc.validationResults(), function(validation) {
        return _.isEqual(validation.path, item.path);
      });
      return validation && validation.result;
    });

    var errorMessage = ko.pureComputed(function() {
      var errType = result() && result()[0];
      return errType && errType !== "tip" && loc(["error", result()[1]]);
    });

    var signalClasses = ko.pureComputed(function() {
      var classes = [];
      var res = result() ? result()[0] : undefined;
      if (res) {
        classes.push(res);
      }
      classes.push(self.size);
      return classes.join(" ");
    });

    var showMessagePanel = ko.observable(false);

    return {
      css: signalClasses,
      errorMessage: errorMessage,
      showMessagePanel: showMessagePanel,
      events: {
        mouseover: function() { showMessagePanel(true); },
        mouseout: function() { showMessagePanel(false); },
        change: function() { save(item); }
      }
    };
  }

  self.rows = ko.pureComputed(function() {
    var doc = self.service.findDocumentById(self.documentId);
    return _.map(self.groups(), function(group) {
      return _.extend(group, {
        info: _.map(group.model, _.partial(cellInfo, doc))
      });
    });
  });
};