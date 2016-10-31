(function() {
  "use strict";

  var attachmentsService = lupapisteApp.services.attachmentsService;

  var constructEditableMetadata = function(actualMetadata, schema) {
    var newMap = {};
    _.forEach(schema, function (v) {
      if (v.dependencies) {
        _.forEach(v.dependencies, function (depArray) {
          _.forEach(depArray, function (depVal) {
            newMap[depVal.type] = ko.observable(actualMetadata && actualMetadata[depVal.type] !== undefined ? actualMetadata[depVal.type] : null);
          });
        });
      }
      if (v.subfields) {
        newMap[v.type] = ko.mapping.fromJS(constructEditableMetadata(actualMetadata ? actualMetadata[v.type] : null, v.subfields));
      } else {
        newMap[v.type] = ko.observable(actualMetadata && actualMetadata[v.type] !== undefined ? actualMetadata[v.type] : null);
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

  var coerceValuesToSchemaType = function(metadata, inputTypeMap, uneditableFieldMap) {
    var uneditableFields = _.keys(_.pickBy(uneditableFieldMap, _.isNumber));
    return _.mapValues(_.omit(metadata, uneditableFields), function(v, k) {
      if (_.isObject(v)) {
        return coerceValuesToSchemaType(v, inputTypeMap[k], uneditableFieldMap[k]);
      } else if(inputTypeMap[k] === "number") {
        return parseInt(v, 10);
      } else if(inputTypeMap[k] === "checkbox" && v === null) {
        return false;
      } else {
        return v;
      }
    });
  };

  var isValid = function(value, requiredType) {
    if (requiredType === "checkbox") {
      return true;
    } else if (value !== undefined && value !== null) {
      if (requiredType === "number") {
        return !isNaN(value) && _.isFinite(parseInt(value, 10));
      } else if (requiredType === "text") {
        return value.length > 0;
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
          if (!depVal.calculated && !isValid(metadata[depVal.type], depVal.inputType)) {
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

  var getForbiddenFields = function(schema, roles, data) {
    var naughtyFields = [];
    _.forEach(schema, function (attribute) {
      if (attribute["require-role"] && !_.includes(roles, attribute["require-role"])) {
        naughtyFields.push(attribute.type);
      }
    });
    if (util.getIn(data, ["sailytysaika", "arkistointi"]) && ko.unwrap(data.sailytysaika.arkistointi) === "ikuisesti") {
      naughtyFields.push("sailytysaika");
    }
    return naughtyFields;
  };

  var getUneditableFields = function(schema) {
    var uneditableFields = {};
    _.forEach(schema, function(v) {
      if (v.dependencies) {
        _.forEach(v.dependencies, function(depArray) {
          _.forEach(depArray, function(depVal) {
            if (depVal.calculated) {
              uneditableFields[depVal.type] = 1;
            }
          });
        });
      }
      if (v.subfields) {
        uneditableFields[v.type] = getUneditableFields(v.subfields);
      } else if (v.calculated) {
        uneditableFields[v.type] = 1;
      }
    });
    return uneditableFields;
  };

  function MetadataEditorModel(params) {
    var self = this;

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

    self.attachmentId = ko.unwrap(params.attachmentId);
    self.applicationId = ko.unwrap(params.application.id);
    self.metadata = ko.isObservable(params.metadata) ? params.metadata : ko.observable(params.metadata);
    self.editable = ko.observable(false);
    self.editedMetadata = ko.observable();
    self.schema = ko.observableArray();
    self.inputTypeMap = {};
    self.disabledFields = ko.observableArray();
    var uneditableFields;

    var authModel = attachmentsService.authModels()[self.attachmentId] || lupapisteApp.models.applicationAuthModel;
    var orgAuthz = ko.unwrap(lupapisteApp.models.currentUser.orgAuthz);
    var organization = ko.unwrap(params.application.organization);
    var roles = orgAuthz && organization ? ko.unwrap(orgAuthz[organization]) : [];

    var saveCommand = "store-tos-metadata-for-application";
    if (params.caseFile) {
      saveCommand = "store-tos-metadata-for-process";
    } else if (self.attachmentId) {
      saveCommand = "store-tos-metadata-for-attachment";
    }

    self.invalidFields = self.disposedPureComputed(function () {
      return validateMetadata(ko.mapping.toJS(self.editedMetadata), self.schema());
    });

    self.disposedSubscribe(self.metadata, function(newValue) {
      // If metadata changes outside this component, we update the new values to the local copy
      if (!_.isEmpty(self.schema()) && !_.isEmpty(newValue)) {
        var newData = constructEditableMetadata(ko.mapping.toJS(newValue), self.schema(), roles);
        self.editedMetadata(newData);
      }
    });

    ajax.query("tos-metadata-schema", {schema: params.caseFile ? "caseFile" : "document"})
      .success(function(data) {
        self.editedMetadata(constructEditableMetadata(ko.mapping.toJS(self.metadata), data.schema, roles));
        self.inputTypeMap = constructSchemaInputTypeMap(data.schema);
        self.schema(data.schema);
        self.disabledFields(getForbiddenFields(data.schema, roles, self.metadata()));
        uneditableFields = getUneditableFields(self.schema());
      })
      .call();

    self.edit = function() {
      self.editable(true);
    };

    self.cancelEdit = function() {
      self.editedMetadata(constructEditableMetadata(ko.mapping.toJS(self.metadata), self.schema(), roles));
      self.editable(false);
    };

    self.modificationAllowed = self.disposedPureComputed(function() {
      return authModel.ok(saveCommand) && !_.includes(["arkistoitu", "arkistoidaan"], util.getIn(self.metadata, ["tila"]));
    });

    self.save = function() {
      var metadata = coerceValuesToSchemaType(ko.mapping.toJS(self.editedMetadata), self.inputTypeMap, uneditableFields);

      ajax.command(saveCommand)
        .json({id: self.applicationId, attachmentId: self.attachmentId, metadata: metadata})
        .success(function(data) {
          self.metadata(ko.mapping.fromJS(data.metadata));
          self.editedMetadata(constructEditableMetadata(data.metadata, self.schema(), roles));
          self.editable(false);
          if (util.getIn(data, ["metadata", "sailytysaika", "arkistointi"]) && ko.unwrap(data.metadata.sailytysaika.arkistointi) === "ikuisesti") {
            self.disabledFields.push("sailytysaika");
          }
          attachmentsService.queryOne(self.attachmentId);
        })
        .call();
    };
  }

  ko.components.register("metadata-editor", {
    viewModel: MetadataEditorModel,
    template: {element: "metadata-editor-template"}
  });

})();
