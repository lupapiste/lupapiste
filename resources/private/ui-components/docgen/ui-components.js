var uiComponents = (function() {
  "use strict";

  var sizeClasses = { "t": "tiny", "s": "short", "m": "medium", "l": "long"};

  // TODO: show warning indicator
  // TODO: refactor
  var saveMany = function(command, documentId, applicationId, element, paths, vals, indicator, result, cb) {
    cb = cb || _.noop;
    var updates = _(paths)
      .map(function(p) { return p.join("."); })
      .zip(vals)
      .value();
    ajax
      .command(command, {
        doc: documentId,
        id: applicationId,
        updates: updates,
        collection: "documents"})
      .success(function (e) {
        _(e.result).filter(function(r) {
          return _.contains(paths, r.path);
        }).forEach(function(r) {
          result(r.result);
        }).value();
        indicator({type: "saved"});
        cb();
      })
      .error(function () {
        indicator({type: "err"});
      })
      .fail(function () {
        indicator({type: "err"});
      })
      .call();
  };

  var save = function(command, documentId, applicationId, element, path, val, indicator, result, cb) {
    return saveMany(command, documentId, applicationId, element, [path], [val], indicator, result, cb);
  };


  var copyRow = function(documentId, applicationId, path, sourceIndex, targetIndex, indicator, result, cb) {
    cb = cb || _.noop;
    ajax
      .command("copy-row", {
        doc: documentId,
        id: applicationId,
        path: path,
        "source-index": sourceIndex,
        "target-index": targetIndex,
        collection: "documents"})
      .success(function (e) {
        repository.load(applicationId);
        var res = _.find(e.results, function(result) {
          return _.isEqual(result.path, path);
        });
        result(res ? res.result : undefined);
        indicator({type: "saved"});
        cb(e);
      })
      .error(function () {
        indicator({type: "err"});
      })
      .fail(function () {
        indicator({type: "err"});
      })
      .call();
  };

  var removeRow = function (documentId, applicationId, path, indicator, result, cb) {
    ajax
      .command("remove-document-data", {
        doc: documentId,
        id: applicationId,
        path: path,
        collection: "documents"
      })
      .success(function(e) {
        var res = _.find(e.results, function(result) {
          return _.isEqual(result.path, path);
        });
        result(res ? res.result : undefined);
        indicator({type: "saved"});
        cb(e);
      })
      .error(function () {
        indicator({type: "err"});
      })
      .fail(function () {
        indicator({type: "err"});
      })
      .call();
  };

  return {
    sizeClasses: sizeClasses,
    save: save,
    saveMany: saveMany,
    copyRow: copyRow,
    removeRow: removeRow
  };

})();
