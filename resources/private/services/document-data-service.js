LUPAPISTE.DocumentDataService = function() {
  "use strict";

  var self = this;
  self.applicationId = null;
  self.propertyId = null;
  self.model = ko.observableArray();

  // Application

  self.setApplication = function(application) {
    self.applicationId = application.id;
    self.propertyId = application.propertyId;
  }

  // Document

  self.addDocument = function(document, options) {
    if (findDocumentById(document.id)) {
      return -1;
    } else {
      return self.model.push({
        id: document.id,
        name: document.schema.info.name,
        isDisabled: options && options.disabled,
        model: createDataModel(_.extend({}, document.schema.info, document.schema), document.data)
      });
    }
  }

  self.findDocumentById = function(id) {
    return _.find(self.model(), function(doc) {
      return doc.id === id;
    });    
  }

  self.getInDocument = function(documentId, path) {
    return getIn(findDocumentById(documentId), path);
  }

  self.removeDocument = function(id) {
    return self.model.remove(findDocumentById(id));
  }

  //
  // Public utilities
  //

  self.createDataModel = createDataModel;
  self.getAsRaw = getAsRaw;

  //
  // Repeating utilities
  //

  function createRepeatingUnitDataModel(schema, rawModel, index) {
    return {
      index: index, 
      model: createGroupDataModel(schema, rawModel)
    };
  }

  function createRepeatingDataModel(schema, rawModel) {
    return ko.observableArray(_.map(rawModel, function(subModel, index) {
      return createRepeatingUnitDataModel(schema, subModel, index);
    }));
  }

  function findByIndex(repeatingModel, index) {
    return _.find(repeatingModel(), function(rep) {
      return rep.index === index;
    });
  }

  function removeByIndex(repeatingModel, index) {
    return repeatingModel.remove(findByIndex(repeatingModel, index));
  }

  function pushToRepeating(schema, repeatingModel, rawModel) {
    var ind = _(repeatingModel()).map('index').max() + 1;
    return repeatingModel.push(createRepeatingUnitDataModel(schema, rawModel, _.max([0, ind])));
  }

  //
  // Group utilities
  //

  function createGroupDataModel(schema, rawModel) {
    return _(schema.body).map(function(subSchema) {
      return [subSchema.name, createDataModel(subSchema, rawModel[subSchema.name])];
    }).zipObject().value();
  }

  //
  // Input utilities
  //

  function createInputDataModel(schema, rawModel) {
    return ko.observable(rawModel && rawModel.value || rawModel);
  }

  //
  // General utilities
  //

  function isRepeating(schema) {
    return schema.repeating || schema.type === 'table';
  }

  function isGroupType(schema) {
    return _.contains(['group', 'table'], schema.type);
  }

  function createDataModel(schema, rawModel) {
    if (isRepeating(schema)) {
      return createRepeatingDataModel(schema, rawModel);
    } else if (isGroupType(schema)) {
      return createGroupDataModel(schema, rawModel);
    } else {
      return createInputDataModel(schema, rawModel);
    }
  }

  function get(model, key) {
    if (ko.isObservableArray(model)) {
      return findByIndex(model(), key);
    } else if (ko.isObservable(model)) {
      return model()[key];
    } else {
      return model[key];
    }
  }

  function getIn(model, path) {
    return _.reduce(path, get, model);
  }

  function getAsRaw(model) {
    if (ko.isObservable(model)) {
      return getTree(model());
    } else if (_.has(_.first(model), 'index')) {
      return _(model)
        .map(function(rep) { return [rep.index, rep.model]; })
        .zipObject()
        .mapValues(getTree)
        .value();
    } else if (_.isObject(model)) {
      return _.mapValues(model, getTree);
    } else {
      return {value: model};
    }
  }
}