LUPAPISTE.DocumentDataService = function(params) {
  "use strict";

  var self = this;
  params = params || {};

  self.model = ko.observableArray();
  self.applicationId = params.readOnly ? ko.observable() : lupapisteApp.models.application.id;

  // Document

  self.findDocumentById = function(id) {
    return _.find(self.model.peek(), function(doc) {
      return doc.id === id;
    });
  };

  function getDefaults(doc) {
    var docType = doc.schema.info.type;
    switch(docType) {
      case "task":
        return {updateCommand: "update-task", removeCommand: "remove-document-data", collection: "tasks"};
      default:
        return {updateCommand: "update-doc",  removeCommand: "remove-document-data", collection: "documents"};
    }
  }

  function resolveCommandNames(doc, options) {
    var docDefaults = getDefaults(doc);
    return _.extend(docDefaults, _.pick(options, "updateCommand", "removeCommand"));
  }

  self.addDocument = function(doc, options) {
    self.model.remove(self.findDocumentById(doc.id));
    return self.model.push( _.extend({
      id: doc.id,
      path: [],
      name: doc.schema.info.name,
      state: doc.state,
      schema: doc.schema,
      isDisabled: options && options.disabled,
      validationResults: ko.observableArray(doc.validationErrors)
      },
      resolveCommandNames(doc, options),
      createDataModel(_.extend({type: "document"}, doc.schema.info, doc.schema), doc.data, [])
    ));
  };

  self.getInDocument = function(documentId, path) {
    var doc = self.findDocumentById(documentId);
    return doc && getIn(doc, path);
  };

  self.getValueIn = function(model, path) {
    var data = getIn(model, path);
    return data && data.model();
  };

  self.removeDocument = function(id) {
    return self.model.remove(self.findDocumentById(id));
  };

  self.addRepeatingGroup = function(documentId, path) {
    var repeatingModel = self.getInDocument(documentId, path);
    return pushToRepeating(repeatingModel, {});
  };

  self.copyRepeatingGroup = function(documentId, path, index, indicator) {
    var repeatingModel = self.getInDocument(documentId, path);
    var rawModel = getAsRaw(findByIndex(repeatingModel, index));
    var repLength = pushToRepeating(repeatingModel, rawModel);
    var updates = getAsUpdates(repeatingModel.model()[repLength - 1]);
    self.updateDoc(documentId,
                   updates,
                   indicator);
  };

  self.getRemoveCommand = function(documentId) {
    var doc = self.findDocumentById(documentId);
    return doc && doc.removeCommand || "remove-document-data";
  };

  self.getUpdateCommand = function(documentId) {
    var doc = self.findDocumentById(documentId);
    return doc && doc.updateCommand || "update-doc";
  };

  self.getTargetCollection = function(documentId) {
    var doc = self.findDocumentById(documentId);
    return doc && doc.collection || "documents";
  };

  self.removeRepeatingGroup = function(documentId, path, index, indicator, extCallback) {
    var repeatingModel = self.getInDocument(documentId, path);
    var cb = function(e) {
      removeByIndex(repeatingModel, index);
      if (extCallback) {
        extCallback(e);
      }
    };
    var params = {
      path: path.concat(index)
    };
    command(self.getRemoveCommand(documentId), self.getTargetCollection(documentId), documentId, params, {
      indicator: indicator,
      cb: cb
    });
  };

  self.updateDoc = function(documentId, updates, indicator, cb) {
    var params = {
      updates: _.map(updates, function(update) {
        return [update[0].join("."), update[1]];
      })
    };
    command(self.getUpdateCommand(documentId), self.getTargetCollection(documentId), documentId, params, {
      indicator: indicator,
      cb: cb
    });
  };

  function command(commandName, collection, documentId, params, opts) {
    var indicator = opts.indicator || _.noop;
    var cb = opts.cb || _.noop;
    ajax
      .command(commandName, _.extend({
          doc: documentId,
          id: self.applicationId(),
          collection: collection
        },
        params)
      )
      .success(function(e) {
        var doc = self.findDocumentById(documentId);
        if (doc) {
          doc.validationResults(e.results);
        }
        indicator({type: "saved"});
        cb(e);
      })
      .error(function (e) {
        var doc = self.findDocumentById(documentId);
        if (doc) {
          doc.validationResults(e.results);
        }
        indicator({type: "err"});
      })
      .fail(function () {
        indicator({type: "err"});
      })
      .call();
  }

  // Schema support

  // Returns true if either the current user is on the whitelist
  // (action is allowed) or no whitelist is defined in the given
  // schema.
  self.isWhitelisted = function( schema ) {
    return !(util.getIn(schema, ["whitelist", "otherwise"]) === "disabled"
            && !_.includes(util.getIn(schema, ["whitelist", "roles"]),
                           lupapisteApp.models.currentUser.applicationRole()));
  };

  //
  // Repeating utilities
  //

  function createRepeatingUnitDataModel(schema, rawModel, path, index) {
    index = index.toString();
    return _.extend(
        {index: index},
         createGroupDataModel(schema, rawModel, path.concat(index))
    );
  }

  function createRepeatingDataModel(schema, rawModel, path) {
    util.dissoc(rawModel, "validationResult");
    return {
      path: path,
      schema: schema,
      model: ko.observableArray(_.map(rawModel, function(subModel, index) {
        return createRepeatingUnitDataModel(schema, subModel, path, index);
      }))
    };
  }

  function findByIndex(repeatingModel, index) {
    return _.find(repeatingModel.model ? repeatingModel.model() : repeatingModel(), function(rep) {
      return _.parseInt(rep.index) === _.parseInt(index);
    });
  }

  function removeByIndex(repeatingModel, index) {
    return repeatingModel.model.remove(findByIndex(repeatingModel, index));
  }

  function pushToRepeating(repeatingModel, rawModel) {
    var ind = _(repeatingModel.model()).map("index").map(_.parseInt).max() + 1 || 0;
    var path = repeatingModel.path;
    return repeatingModel.model.push(createRepeatingUnitDataModel(repeatingModel.schema, rawModel, path, ind));
  }

  //
  // Group utilities
  //

  function createGroupDataModel(schema, rawModel, path) {
    return {
      path: path,
      schema: schema,
      model: _(schema.body).map(function(subSchema) {
        return [subSchema.name, createDataModel(subSchema, rawModel && rawModel[subSchema.name], path.concat(subSchema.name))];
      }).fromPairs().value()
    };
  }

  //
  // Input utilities
  //

  function createInputDataModel(schema, rawModel, path) {
    return {path: path,
            schema: schema,
            model: ko.observable(rawModel && rawModel.value)};
  }

  //
  // General utilities
  //

  function isRepeating(schema) {
    return schema.repeating && !_.includes(["document" ,"party", "location"], schema.type);
  }

  function isGroupType(schema) {
    return _.includes(["group", "table", "location", "document", "party", "task"], schema.type);
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
    if (_.has(model, "model")) {
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
    var recur = function(dataModel) {
      if (_.has(dataModel, "model")) {
        return recur(dataModel.model);

      } else if (_.isArray(dataModel)) {
        return _(dataModel)
          .map(function(rep) { return [rep.index, rep.model]; })
          .fromPairs()
          .mapValues(recur)
          .value();

      } else if (_.isObject(dataModel)) {
        return _.mapValues(dataModel, recur);

      } else {
        return {value: dataModel};
      }
    };
    return recur(ko.toJS(model));
  }

  function getAsUpdates(dataModel) {
    var recur = function(dataModel) {
      if (_.isObject(dataModel.model)) {
        return _(dataModel.model)
          .map(recur)
          .flatten()
          .filter(1)
          .value();

      } else {
        return [[dataModel.path, dataModel.model]];
      }
    };
    return recur(ko.toJS(dataModel));
  }
};
