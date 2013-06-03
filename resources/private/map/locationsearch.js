// FIXME: SELF
var locationSearch = (function() {

  var serchPointByAddress = function(requestContext, address, onSuccess, onFail) {
    ajax
      .get("/proxy/get-address")
      .param("query", address)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
    return self;
  };

  var searchPointByPropertyId = function(requestContext, propertyId, onSuccess, onFail) {
    ajax
      .get("/proxy/point-by-property-id")
      .param("property-id", util.prop.toDbFormat(propertyId))
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
    return self;
  };

  var searchPropertyId = function(requestContext, x, y, onSuccess, onFail) {
    ajax
      .get("/proxy/property-id-by-point")
      .param("x", x)
      .param("y", y)
      .success(requestContext.onResponse(onSuccess))
      .fail(requestContext.onResponse(onFail))
      .call();
    return self;
  };

  var searchAddress = function(requestContext, x, y, onSuccess) {
    ajax
      .get("/proxy/address-by-point")
      .param("x", x)
      .param("y", y)
      .success(requestContext.onResponse(onSuccess))
      .call();
    return self;
  };

  return {
    pointByAddress: serchPointByAddress,
    pointByPropertyId: searchPointByPropertyId,
    propertyIdByPoint: searchPropertyId,
    addressByPoint: searchAddress
  };
})();
