package com.avaje.ebeaninternal.server.expression;

import com.avaje.ebeaninternal.api.HashQueryPlanBuilder;
import com.avaje.ebeaninternal.api.SpiExpression;
import com.avaje.ebeaninternal.api.SpiExpressionRequest;
import com.avaje.ebeaninternal.server.el.ElPropertyValue;

import java.io.IOException;

class CaseInsensitiveEqualExpression extends AbstractValueExpression {

  CaseInsensitiveEqualExpression(String propertyName, Object value) {
    super(propertyName, value);
  }

  /**
   * Return the bind value taking into account named parameters.
   */
  private String val() {
    return strValue().toLowerCase();
  }

  @Override
  public void writeDocQuery(DocQueryContext context) throws IOException {
    context.writeIEqualTo(propName, val());
  }

  @Override
  public void addBindValues(SpiExpressionRequest request) {

    ElPropertyValue prop = getElProp(request);
    if (prop != null && prop.isDbEncrypted()) {
      // bind the key as well as the value
      String encryptKey = prop.getBeanProperty().getEncryptKey().getStringValue();
      request.addBindEncryptKey(encryptKey);
    }

    request.addBindValue(val());
  }

  @Override
  public void addSql(SpiExpressionRequest request) {

    String pname = propName;
    ElPropertyValue prop = getElProp(request);
    if (prop != null && prop.isDbEncrypted()) {
      pname = prop.getBeanProperty().getDecryptProperty(propName);
    }

    request.append("lower(").append(pname).append(") =? ");
  }

  @Override
  public void queryPlanHash(HashQueryPlanBuilder builder) {
    builder.add(CaseInsensitiveEqualExpression.class).add(propName);
    builder.bind(1);
  }

  @Override
  public int queryBindHash() {
    return val().hashCode();
  }

  @Override
  public boolean isSameByPlan(SpiExpression other) {
    if (!(other instanceof CaseInsensitiveEqualExpression)) {
      return false;
    }

    CaseInsensitiveEqualExpression that = (CaseInsensitiveEqualExpression) other;
    return this.propName.equals(that.propName);
  }

  @Override
  public boolean isSameByBind(SpiExpression other) {
    CaseInsensitiveEqualExpression that = (CaseInsensitiveEqualExpression) other;
    return val().equals(that.val());
  }
}
