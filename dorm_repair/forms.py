"""
表单定义与校验（相当于 Java 版 dto 包中带校验注解的表单类）。

FlaskForm 会自动校验 CSRF token；校验失败的提示在页面上显示在对应输入框下方。
需要查数据库的规则（学号是否已注册、原密码是否正确等）在 services 中处理。
"""
from flask_wtf import FlaskForm
from wtforms import (IntegerField, PasswordField, RadioField, SelectField, StringField,
                     TextAreaField, ValidationError)
from wtforms.validators import DataRequired, EqualTo, Length, NumberRange, Optional, Regexp

from .constants import BUILDINGS, RepairCategory

STUDENT_NO = Regexp(r"^[A-Za-z0-9]{4,20}$", message="学号为 4~20 位字母或数字")
ROOM_NO = Regexp(r"^[A-Za-z0-9-]{1,10}$", message="宿舍号为 1~10 位字母、数字或 -，如 302")
PHONE = Regexp(r"^1\d{10}$", message="请输入 11 位手机号")
BUILDING_CHOICES = [("", "请选择")] + [(b, b) for b in BUILDINGS]


def validate_building(form, field):
    """楼栋只能从预设列表中选择，防止伪造请求提交任意值。"""
    if field.data and field.data not in BUILDINGS:
        raise ValidationError("请选择正确的楼栋")


class BaseForm(FlaskForm):
    class Meta:
        # 使用 WTForms 自带的中文提示（如"不是有效的整数"）
        locales = ["zh"]


class LoginForm(BaseForm):
    username = StringField("账号", validators=[DataRequired("请输入账号")])
    password = PasswordField("密码", validators=[DataRequired("请输入密码")])


class RegisterForm(BaseForm):
    """学生注册表单。没有"角色"字段：注册出来的账号在 service 中固定为学生。"""
    username = StringField("学号", validators=[DataRequired("请输入学号"), STUDENT_NO])
    real_name = StringField("姓名", validators=[DataRequired("请输入姓名"), Length(max=20, message="姓名不能超过 20 个字")])
    building = SelectField("楼栋", choices=BUILDING_CHOICES, validate_choice=False,
                           validators=[DataRequired("请选择楼栋"), validate_building])
    room_no = StringField("宿舍号", validators=[DataRequired("请输入宿舍号"), ROOM_NO])
    phone = StringField("手机号", validators=[DataRequired("请输入手机号"), PHONE])
    password = PasswordField("密码", validators=[DataRequired("请输入密码"),
                                               Length(min=6, max=20, message="密码长度为 6~20 位")])
    confirm_password = PasswordField("确认密码", validators=[DataRequired("请再次输入密码"),
                                                         EqualTo("password", message="两次输入的密码不一致")])


class ProfileForm(BaseForm):
    """修改个人信息。楼栋、宿舍号只对学生必填，在 user_service 中按角色判断。"""
    real_name = StringField("姓名", validators=[DataRequired("请输入姓名"), Length(max=20, message="姓名不能超过 20 个字")])
    phone = StringField("手机号", validators=[DataRequired("请输入手机号"), PHONE])
    building = SelectField("楼栋", choices=BUILDING_CHOICES, validate_choice=False,
                           validators=[Optional(), validate_building])
    room_no = StringField("宿舍号", validators=[Optional(), ROOM_NO])


class PasswordForm(BaseForm):
    old_password = PasswordField("原密码", validators=[DataRequired("请输入原密码")])
    new_password = PasswordField("新密码", validators=[DataRequired("请输入新密码"),
                                                    Length(min=6, max=20, message="新密码长度为 6~20 位")])
    confirm_password = PasswordField("确认新密码", validators=[DataRequired("请再次输入新密码"),
                                                          EqualTo("new_password", message="两次输入的新密码不一致")])


class OrderForm(BaseForm):
    """提交报修（图片通过 request.files 单独接收）。"""
    building = SelectField("楼栋", choices=BUILDING_CHOICES, validate_choice=False,
                           validators=[DataRequired("请选择楼栋"), validate_building])
    room_no = StringField("宿舍号", validators=[DataRequired("请输入宿舍号"), ROOM_NO])
    category = RadioField("报修类型", choices=[(c.name, c.label) for c in RepairCategory],
                          validate_choice=False, validators=[DataRequired("请选择报修类型")])
    description = TextAreaField("故障描述", validators=[DataRequired("请填写故障描述"),
                                                    Length(max=500, message="故障描述不能超过 500 字")])

    def validate_category(self, field):
        """报修类型只能是写死的四种之一。"""
        if field.data not in RepairCategory.__members__:
            raise ValidationError("请选择正确的报修类型")


class RateForm(BaseForm):
    rating = IntegerField("星级", validators=[DataRequired("请选择星级"),
                                            NumberRange(min=1, max=5, message="星级为 1~5")])
    evaluation = TextAreaField("评价内容", validators=[Length(max=200, message="评价内容不能超过 200 字")])


class CompleteForm(BaseForm):
    repair_note = TextAreaField("维修备注", validators=[DataRequired("请填写维修备注"),
                                                    Length(max=500, message="维修备注不能超过 500 字")])


def first_error(form) -> str:
    """取第一条校验错误作为提示语（用于"跳转后提示"的场景，如评价、完成维修）。"""
    for errors in form.errors.values():
        if errors:
            return errors[0]
    return "输入信息有误"
