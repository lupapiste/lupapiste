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

  self.findDocumentById = function(id) {
    return _.find(self.model(), function(doc) {
      return doc.id === id;
    });    
  }

  self.addDocument = function(doc, options) {
    var docData = {
      id: doc.id,
      name: doc.schema.info.name,
      isDisabled: options && options.disabled
    };
    if (self.findDocumentById(doc.id)) {
      return -1;
    } else {
      return self.model.push( _.extend( docData,
        createDataModel(_.extend({type: 'document'}, doc.schema.info, doc.schema), doc.data, [doc.schema.info.name])
      ));
    }
  }

  self.getInDocument = function(documentId, path) {
    var doc = self.findDocumentById(documentId);
    return doc && getIn(doc, path);
  }

  self.removeDocument = function(id) {
    return self.model.remove(self.findDocumentById(id));
  }

  //
  // Public utilities
  //

  self.createDataModel = createDataModel;
  self.getAsRaw = getAsRaw;

  //
  // Repeating utilities
  //

  function createRepeatingDataModel(schema, rawModel, path) {
    return ko.observableArray(_.map(rawModel, function(subModel, index) {
      return _.extend({index: index}, createGroupDataModel(schema, subModel, path.concat(index)));
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
    var path = repeatingModel() && repeatingModel().path;
    return repeatingModel.push(createRepeatingUnitDataModel(schema, rawModel, path, _.max([0, ind])));
  }

  //
  // Group utilities
  //

  function createGroupDataModel(schema, rawModel, path) {
    return {
      path: path,
      model: _(schema.body).map(function(subSchema) {
        return [subSchema.name, createDataModel(subSchema, rawModel[subSchema.name], path.concat(subSchema.name))]
      }).zipObject().value()
    };
  }

  //
  // Input utilities
  //

  function createInputDataModel(schema, rawModel, path) {
    return {path: path,
            model: ko.observable(rawModel && rawModel.value)};
  }

  //
  // General utilities
  //

  function isRepeating(schema) {
    return schema.repeating || schema.type === 'table';
  }

  function isGroupType(schema) {
    return _.contains(['group', 'table', 'location', 'document'], schema.type);
  }

  function createDataModel(schema, rawModel, path) {
    if (isRepeating(schema)) {
      return createRepeatingDataModel(schema, rawModel, path);
    } else if (isGroupType(schema)) {
      return createGroupDataModel(schema, rawModel, path);
    } else {
      return createInputDataModel(schema, rawModel, path);
    }
  }

  function get(model, key) {
    if (_.has(model, 'model')) {
      return get(model.model, key);
    } else if (ko.isObservable(model) && _.isArray(model())) {
      return findByIndex(model, key);
    } else if (ko.isObservable(model)) {
      return model() && model()[key];
    } else if (_.isObject(model)) {
      return model[key];
    }
  }

  function getIn(model, path) {
    return _.reduce(path, get, model);
  }

  function getAsRaw(model) {

    if (ko.isObservable(model)) {
      return getAsRaw(model());

    } else if (_.has(model, 'model')) {
      return getAsRaw(model.model);

    } else if (_.has(_.first(model), 'index')) {
      return _(model)
        .map(function(rep) { return [rep.index, rep.model]; })
        .zipObject()
        .mapValues(getAsRaw)
        .value();

    } else if (_.isObject(model)) {
      return _.mapValues(model, getAsRaw);

    } else {
      return {value: model};
    }
  }
}