(function() {
  "use strict";

  var constructEditableMetadata = function(actualMetadata, schema) {
    var newMap = {};
    _.forEach(schema, function (v) {
      if (v.dependencies) {
        _.forEach(v.dependencies, function (depArray) {
          _.forEach(depArray, function (depVal) {
            newMap[depVal.type] = actualMetadata && actualMetadata[depVal.type] ? actualMetadata[depVal.type] : null;
          });
        });
      }
      if (v.subfields) {
        newMap[v.type] = constructEditableMetadata(actualMetadata[v.type], v.subfields);
      } else {
        newMap[v.type] = actualMetadata && actualMetadata[v.type] ? actualMetadata[v.type] : null;
      }
    });
    return newMap;
  };

  var constructSchemaInputTypeMap = function(schema) {
    var newMap = {};
    _.forEach(schema, function(v) {
      if (v.dependencies) {
        _.forEach(v.dependencies, function(depArray) {
          _.forEach(depArray, function(depVal) {
            newMap[depVal.type] = depVal.inputType || "keyword";
          });
        });
      }
      if (v.subfields) {
        newMap[v.type] = constructSchemaInputTypeMap(v.subfields);
      } else {
        newMap[v.type] = v.inputType || "keyword";
      }
    });
    return newMap;
  };

  var coerceValuesToSchemaType = function(metadata, inputTypeMap) {
    return _.mapValues(metadata, function(v, k) {
      if (_.isObject(v)) {
        return coerceValuesToSchemaType(v, inputTypeMap[k]);
      } else if(inputTypeMap[k] === "number") {
        return parseInt(v);
      } else {
        return v;
      }
    });
  };

  var isValid = function(value, requiredType) {
    if (value) {
      if (requiredType === "number") {
        return !isNaN(value) && _.isFinite(parseInt(value));
      } else {
        return true;
      }
    } else {
      return false;
    }
  };

  var validateMetadata = function(metadata, schema) {
    var errors = [];
    _.forEach(schema, function(v) {
      if (v.dependencies && v.dependencies[metadata[v.type]]) {
        _.forEach(v.dependencies[metadata[v.type]], function(depVal) {
          if (!isValid(metadata[depVal.type], depVal.inputType)) {
            errors.push(depVal.type);
          }
        });
      }
      if (v.subfields) {
        errors = errors.concat(validateMetadata(metadata[v.type], v.subfields));
      } else if (!isValid(metadata[v.type], v.inputType)) {
        errors.push(v.type);
      }
    });
    return errors;
  };

  var model = function(params) {
    var self = this;
    self.attachmentId = params.attachmentId ? params.attachmentId : ko.observable(null);
    self.applicationId = params.applicationId;
    self.metadata = params.metadata;
    self.editable = ko.observable(false);
    self.editedMetadata = ko.observable();
    self.schema = ko.observableArray();
    self.inputTypeMap = {};

    // It does not seem to be possible to use visible: !editable, so we need another observable for that.
    self.displayOnly = ko.pureComputed(function() {
      return !self.editable();
    });

    self.invalidFields = ko.pureComputed(function () {
      return validateMetadata(ko.mapping.toJS(self.editedMetadata), self.schema());
    });

    ajax.query("tos-metadata-schema")
      .success(function(data) {
        self.editedMetadata(ko.mapping.fromJS(constructEditableMetadata(self.metadata(), data.schema)));
        self.inputTypeMap = constructSchemaInputTypeMap(data.schema);
        self.schema(data.schema);
      })
      .call();

    self.edit = function() {
      self.editable(true);
    };

    self.cancelEdit = function() {
      self.editedMetadata(ko.mapping.fromJS(constructEditableMetadata(self.metadata(), self.schema())));
      self.editable(false);
    };

    self.save = function() {
      var metadata = coerceValuesToSchemaType(ko.mapping.toJS(self.editedMetadata), self.inputTypeMap);
      var command = self.attachmentId() ? "store-tos-metadata-for-attachment" : "store-tos-metadata-for-application";
      ajax.command(command)
        .json({id: self.applicationId(), attachmentId: self.attachmentId(), metadata: metadata})
        .success(function() {
          self.metadata(ko.mapping.toJS(self.editedMetadata));
          self.editable(false);
        })
        .call();
    }
  };

  ko.components.register("metadata-editor", {
    viewModel: model,
    template: {element: "metadata-editor-template"}
  });

})();